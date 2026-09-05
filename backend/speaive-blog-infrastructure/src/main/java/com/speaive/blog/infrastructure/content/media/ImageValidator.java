package com.speaive.blog.infrastructure.content.media;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * 图片内容校验器，按编码结构检查实际字节，降低只检查扩展名或文件头造成的误判。
 */
final class ImageValidator {
    private static final int MAX_DIMENSION = 12_000;
    private static final long MAX_PIXELS = 36_000_000L;
    private static final Set<String> AVIF_CONTAINER_BOXES = Set.of("iprp", "ipco", "moov", "trak", "mdia",
            "minf", "stbl");

    private ImageValidator() {
    }

    static void validate(String mimeType, byte[] bytes) {
        Dimensions dimensions = switch (mimeType) {
            case "image/png" -> validatePng(bytes);
            case "image/jpeg" -> validateJpeg(bytes);
            case "image/gif" -> validateGif(bytes);
            case "image/webp" -> validateWebp(bytes);
            case "image/avif" -> validateAvif(bytes);
            default -> throw invalidImage("不支持的图片格式");
        };
        validateDimensions(dimensions);
    }

    private static Dimensions validatePng(byte[] bytes) {
        if (bytes.length < 45) {
            throw invalidImage("PNG 文件结构不完整");
        }
        int offset = 8;
        Dimensions dimensions = null;
        boolean sawImageData = false;
        boolean sawEnd = false;
        while (offset < bytes.length) {
            if (offset + 12 > bytes.length) {
                throw invalidImage("PNG 数据块不完整");
            }
            long length = unsignedIntBigEndian(bytes, offset);
            if (length > Integer.MAX_VALUE || offset + 12L + length > bytes.length) {
                throw invalidImage("PNG 数据块长度不合法");
            }
            int dataLength = (int) length;
            String type = ascii(bytes, offset + 4, offset + 8);
            int dataStart = offset + 8;
            int crcOffset = dataStart + dataLength;
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, 4 + dataLength);
            if (crc.getValue() != unsignedIntBigEndian(bytes, crcOffset)) {
                throw invalidImage("PNG 数据块校验失败");
            }
            if (offset == 8 && (!type.equals("IHDR") || dataLength != 13)) {
                throw invalidImage("PNG 缺少合法的 IHDR 数据块");
            }
            if (type.equals("IHDR")) {
                dimensions = new Dimensions(unsignedIntBigEndian(bytes, dataStart),
                        unsignedIntBigEndian(bytes, dataStart + 4));
            } else if (type.equals("IDAT")) {
                sawImageData = true;
            } else if (type.equals("IEND")) {
                if (dataLength != 0 || crcOffset + 4 != bytes.length) {
                    throw invalidImage("PNG IEND 数据块不合法");
                }
                sawEnd = true;
            }
            offset = crcOffset + 4;
        }
        if (dimensions == null || !sawImageData || !sawEnd) {
            throw invalidImage("PNG 文件结构不完整");
        }
        verifyWithImageIo(bytes, "PNG");
        return dimensions;
    }

    private static Dimensions validateJpeg(byte[] bytes) {
        if (bytes.length < 12 || unsigned(bytes[0]) != 0xff || unsigned(bytes[1]) != 0xd8) {
            throw invalidImage("JPEG 文件结构不完整");
        }
        Dimensions dimensions = null;
        int offset = 2;
        boolean sawEnd = false;
        while (offset < bytes.length) {
            if (unsigned(bytes[offset]) != 0xff) {
                offset++;
                continue;
            }
            while (offset < bytes.length && unsigned(bytes[offset]) == 0xff) {
                offset++;
            }
            if (offset >= bytes.length) {
                break;
            }
            int marker = unsigned(bytes[offset++]);
            if (marker == 0xd9) {
                sawEnd = true;
                break;
            }
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd8) {
                continue;
            }
            if (offset + 2 > bytes.length) {
                throw invalidImage("JPEG 数据段不完整");
            }
            int segmentLength = unsignedShortBigEndian(bytes, offset);
            if (segmentLength < 2 || offset + segmentLength > bytes.length) {
                throw invalidImage("JPEG 数据段长度不合法");
            }
            if (isStartOfFrame(marker)) {
                if (segmentLength < 7) {
                    throw invalidImage("JPEG 尺寸数据不完整");
                }
                dimensions = new Dimensions(unsignedShortBigEndian(bytes, offset + 5),
                        unsignedShortBigEndian(bytes, offset + 3));
            }
            if (marker == 0xda) {
                sawEnd = findJpegEnd(bytes, offset + segmentLength);
                break;
            }
            offset += segmentLength;
        }
        if (dimensions == null || !sawEnd) {
            throw invalidImage("JPEG 缺少尺寸信息或结束标记");
        }
        verifyWithImageIo(bytes, "JPEG");
        return dimensions;
    }

    private static Dimensions validateGif(byte[] bytes) {
        if (bytes.length < 14 || !(ascii(bytes, 0, 6).equals("GIF87a")
                || ascii(bytes, 0, 6).equals("GIF89a")) || unsigned(bytes[bytes.length - 1]) != 0x3b) {
            throw invalidImage("GIF 文件结构不完整");
        }
        Dimensions dimensions = new Dimensions(unsignedShortLittleEndian(bytes, 6),
                unsignedShortLittleEndian(bytes, 8));
        verifyWithImageIo(bytes, "GIF");
        return dimensions;
    }

    private static Dimensions validateWebp(byte[] bytes) {
        if (bytes.length < 20 || !ascii(bytes, 0, 4).equals("RIFF") || !ascii(bytes, 8, 12).equals("WEBP")
                || unsignedIntLittleEndian(bytes, 4) + 8L != bytes.length) {
            throw invalidImage("WebP RIFF 容器不完整");
        }
        int offset = 12;
        Dimensions dimensions = null;
        boolean sawImagePayload = false;
        while (offset < bytes.length) {
            if (offset + 8 > bytes.length) {
                throw invalidImage("WebP 数据块不完整");
            }
            String type = ascii(bytes, offset, offset + 4);
            long length = unsignedIntLittleEndian(bytes, offset + 4);
            long next = offset + 8L + length + (length & 1L);
            if (length > Integer.MAX_VALUE || next > bytes.length) {
                throw invalidImage("WebP 数据块长度不合法");
            }
            int data = offset + 8;
            if (type.equals("VP8X")) {
                if (length < 10) {
                    throw invalidImage("WebP VP8X 数据块不完整");
                }
                dimensions = new Dimensions(1L + unsigned24LittleEndian(bytes, data + 4),
                        1L + unsigned24LittleEndian(bytes, data + 7));
            } else if (type.equals("VP8L")) {
                if (length < 5 || unsigned(bytes[data]) != 0x2f) {
                    throw invalidImage("WebP VP8L 数据块不完整");
                }
                int b1 = unsigned(bytes[data + 1]);
                int b2 = unsigned(bytes[data + 2]);
                int b3 = unsigned(bytes[data + 3]);
                int b4 = unsigned(bytes[data + 4]);
                dimensions = new Dimensions(1L + (((b2 & 0x3f) << 8) | b1),
                        1L + (((b4 & 0x0f) << 10) | (b3 << 2) | ((b2 & 0xc0) >>> 6)));
                sawImagePayload = true;
            } else if (type.equals("VP8 ")) {
                if (length < 10 || unsigned(bytes[data + 3]) != 0x9d || unsigned(bytes[data + 4]) != 0x01
                        || unsigned(bytes[data + 5]) != 0x2a) {
                    throw invalidImage("WebP VP8 数据块不完整");
                }
                dimensions = new Dimensions(unsignedShortLittleEndian(bytes, data + 6) & 0x3fff,
                        unsignedShortLittleEndian(bytes, data + 8) & 0x3fff);
                sawImagePayload = true;
            } else if (type.equals("ANIM") || type.equals("ANMF")) {
                sawImagePayload = true;
            }
            offset = (int) next;
        }
        if (offset != bytes.length || dimensions == null || !sawImagePayload) {
            throw invalidImage("WebP 缺少合法的图片数据或尺寸信息");
        }
        return dimensions;
    }

    private static Dimensions validateAvif(byte[] bytes) {
        if (bytes.length < 24) {
            throw invalidImage("AVIF 文件结构不完整");
        }
        AvifState state = new AvifState();
        parseAvifBoxes(bytes, 0, bytes.length, 0, false, state);
        if (!state.hasAvifBrand || state.dimensions == null) {
            throw invalidImage("AVIF 缺少品牌或尺寸信息");
        }
        return state.dimensions;
    }

    private static void parseAvifBoxes(
            byte[] bytes, int start, int end, int depth, boolean insideMeta, AvifState state) {
        if (depth > 8) {
            throw invalidImage("AVIF 容器嵌套过深");
        }
        int offset = start;
        while (offset < end) {
            if (offset + 8 > end) {
                throw invalidImage("AVIF 数据盒不完整");
            }
            long declaredSize = unsignedIntBigEndian(bytes, offset);
            String type = ascii(bytes, offset + 4, offset + 8);
            int headerSize = 8;
            long boxSize = declaredSize;
            if (declaredSize == 1) {
                if (offset + 16 > end) {
                    throw invalidImage("AVIF 扩展数据盒不完整");
                }
                boxSize = signedLongBigEndian(bytes, offset + 8);
                headerSize = 16;
            } else if (declaredSize == 0) {
                boxSize = end - offset;
            }
            if (boxSize < headerSize || boxSize > Integer.MAX_VALUE || offset + boxSize > end) {
                throw invalidImage("AVIF 数据盒长度不合法");
            }
            int dataStart = offset + headerSize;
            int boxEnd = (int) (offset + boxSize);
            if (type.equals("ftyp")) {
                if (boxEnd - dataStart < 8 || (boxEnd - dataStart - 8) % 4 != 0) {
                    throw invalidImage("AVIF ftyp 数据盒不合法");
                }
                for (int brandOffset = dataStart; brandOffset + 4 <= boxEnd; brandOffset += 4) {
                    if (brandOffset == dataStart + 4) {
                        continue;
                    }
                    String brand = ascii(bytes, brandOffset, brandOffset + 4);
                    if (brand.equals("avif") || brand.equals("avis")) {
                        state.hasAvifBrand = true;
                    }
                }
            } else if (type.equals("meta")) {
                if (boxEnd - dataStart < 4) {
                    throw invalidImage("AVIF meta 数据盒不完整");
                }
                parseAvifBoxes(bytes, dataStart + 4, boxEnd, depth + 1, true, state);
            } else if (AVIF_CONTAINER_BOXES.contains(type)) {
                parseAvifBoxes(bytes, dataStart, boxEnd, depth + 1, insideMeta, state);
            } else if (type.equals("ispe") && insideMeta) {
                if (boxEnd - dataStart < 12) {
                    throw invalidImage("AVIF ispe 数据盒不完整");
                }
                state.dimensions = new Dimensions(unsignedIntBigEndian(bytes, dataStart + 4),
                        unsignedIntBigEndian(bytes, dataStart + 8));
            }
            offset = boxEnd;
        }
    }

    private static void verifyWithImageIo(byte[] bytes, String formatName) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw invalidImage(formatName + " 文件无法读取");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage(formatName + " 文件无法解码");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                validateDimensions(new Dimensions(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BlogException blogException) {
                throw blogException;
            }
            throw new BlogException(BlogErrorCode.INVALID_IMAGE, formatName + " 文件无法解析", exception);
        }
    }

    private static void validateDimensions(Dimensions dimensions) {
        long width = dimensions.width();
        long height = dimensions.height();
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || width * height > MAX_PIXELS) {
            throw invalidImage("图片尺寸不合法或过大");
        }
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
    }

    private static boolean findJpegEnd(byte[] bytes, int start) {
        for (int index = Math.max(0, start); index + 1 < bytes.length; index++) {
            if (unsigned(bytes[index]) == 0xff && unsigned(bytes[index + 1]) == 0xd9) {
                return true;
            }
        }
        return false;
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static int unsignedShortBigEndian(byte[] bytes, int offset) {
        requireAvailable(bytes, offset, 2);
        return unsigned(bytes[offset]) << 8 | unsigned(bytes[offset + 1]);
    }

    private static int unsignedShortLittleEndian(byte[] bytes, int offset) {
        requireAvailable(bytes, offset, 2);
        return unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8;
    }

    private static long unsignedIntBigEndian(byte[] bytes, int offset) {
        requireAvailable(bytes, offset, 4);
        return (long) unsigned(bytes[offset]) << 24
                | (long) unsigned(bytes[offset + 1]) << 16
                | (long) unsigned(bytes[offset + 2]) << 8
                | unsigned(bytes[offset + 3]);
    }

    private static long unsignedIntLittleEndian(byte[] bytes, int offset) {
        requireAvailable(bytes, offset, 4);
        return unsigned(bytes[offset])
                | (long) unsigned(bytes[offset + 1]) << 8
                | (long) unsigned(bytes[offset + 2]) << 16
                | (long) unsigned(bytes[offset + 3]) << 24;
    }

    private static int unsigned24LittleEndian(byte[] bytes, int offset) {
        requireAvailable(bytes, offset, 3);
        return unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8 | unsigned(bytes[offset + 2]) << 16;
    }

    private static long signedLongBigEndian(byte[] bytes, int offset) {
        requireAvailable(bytes, offset, 8);
        long result = 0;
        for (int index = 0; index < 8; index++) {
            result = result << 8 | unsigned(bytes[offset + index]);
        }
        return result;
    }

    private static String ascii(byte[] bytes, int start, int end) {
        requireAvailable(bytes, start, end - start);
        return new String(bytes, start, end - start, StandardCharsets.US_ASCII);
    }

    private static void requireAvailable(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset + (long) length > bytes.length) {
            throw invalidImage("图片文件结构不完整");
        }
    }

    private static BlogException invalidImage(String message) {
        return new BlogException(BlogErrorCode.INVALID_IMAGE, message);
    }

    private record Dimensions(long width, long height) {
    }

    private static final class AvifState {
        private boolean hasAvifBrand;
        private Dimensions dimensions;
    }
}
