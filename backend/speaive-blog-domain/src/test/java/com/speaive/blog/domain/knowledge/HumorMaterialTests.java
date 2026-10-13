package com.speaive.blog.domain.knowledge;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumorMaterialTests {
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

    @Test
    void createNormalizesSourceAndDistinctTopicsWithoutRetainingMutableInput() {
        List<String> topics = new ArrayList<>(List.of(" 时间 ", "时钟", "时间"));

        HumorMaterial material = HumorMaterial.create(" sample-id ", " 原句 ", " https://example.com/source ",
                " 来源标题 ", HumorSourceKind.PRIMARY_POST, topics, HumorMechanism.LITERAL,
                " 笑点分析 ", " 原创示例 ", 5, true, NOW);
        topics.clear();

        assertEquals("sample-id", material.id());
        assertEquals("原句", material.excerpt());
        assertEquals("https://example.com/source", material.sourceUrl());
        assertEquals("来源标题", material.sourceTitle());
        assertEquals("笑点分析", material.analysis());
        assertEquals("原创示例", material.adaptation());
        assertEquals(List.of("时间", "时钟"), material.topics());
        assertThrows(UnsupportedOperationException.class, () -> material.topics().add("日期"));
        assertEquals(1, material.version());
        assertTrue(material.enabled());
        assertEquals(NOW, material.createdAt());
        assertEquals(NOW, material.updatedAt());
    }

    @Test
    void reviseChangesMaterialStateAndAdvancesVersionWhilePreservingIdentityAndCreationTime() {
        HumorMaterial current = sample();

        HumorMaterial updated = current.revise("新原句", "https://example.com/new", "转载来源",
                HumorSourceKind.REPRINT, List.of("收入"), HumorMechanism.DAILY_LIFE,
                "新的生活账本分析", "新的原创示例", 3, false, NOW.plusSeconds(30));

        assertNotSame(current, updated);
        assertEquals(current.id(), updated.id());
        assertEquals(current.createdAt(), updated.createdAt());
        assertEquals(2, updated.version());
        assertEquals(NOW.plusSeconds(30), updated.updatedAt());
        assertEquals(HumorSourceKind.REPRINT, updated.sourceKind());
        assertEquals(HumorMechanism.DAILY_LIFE, updated.mechanism());
        assertEquals(List.of("收入"), updated.topics());
        assertFalse(updated.enabled());
        assertTrue(current.enabled());
        assertEquals(1, current.version());
    }

    @Test
    void rejectsMissingOversizedOrEmptyTopicsBeforePersistence() {
        List<List<String>> invalid = Arrays.asList(null, List.of(), List.of(" "),
                List.of("长".repeat(51)), Arrays.asList("时间", null),
                IntStream.range(0, 13).mapToObj(index -> "主题" + index).toList());
        for (List<String> topics : invalid) {
            assertInvalid(() -> HumorMaterial.create("sample", "原句", "https://example.com/source", "来源",
                    HumorSourceKind.PRIMARY_POST, topics, HumorMechanism.LITERAL, "分析", "原创示例", 4, true, NOW));
        }
    }

    @Test
    void onlyHttpSourcesWithHostAndWithoutUserCredentialsAreAccepted() {
        for (String source : List.of("javascript:alert(1)", "data:text/plain,source", "ftp://example.com/file",
                "/relative/source", "https:///missing-host", "https://user:secret@example.com/source",
                "https://example.com/a b", "https://")) {
            assertInvalid(() -> HumorMaterial.create("sample", "原句", source, "来源",
                    HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                    "分析", "原创示例", 4, true, NOW));
        }
        HumorMaterial plainHttp = HumorMaterial.create("sample", "原句", "http://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL, "分析", "原创示例", 4, true, NOW);
        assertEquals("http://example.com/source", plainHttp.sourceUrl());
    }

    @Test
    void validatesQualityEnumsTextLengthsVersionAndTimestampInvariants() {
        for (int quality : List.of(0, 6)) {
            assertInvalid(() -> HumorMaterial.create("sample", "原句", "https://example.com/source", "来源",
                    HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                    "分析", "原创示例", quality, true, NOW));
        }
        for (String excerpt : Arrays.asList(null, " ", "长".repeat(501), "原\0句")) {
            assertInvalid(() -> HumorMaterial.create("sample", excerpt, "https://example.com/source", "来源",
                    HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                    "分析", "原创示例", 4, true, NOW));
        }
        assertInvalid(() -> HumorMaterial.create("sample", "原句", "https://example.com/source", "来源",
                null, List.of("时间"), HumorMechanism.LITERAL, "分析", "原创示例", 4, true, NOW));
        assertInvalid(() -> HumorMaterial.create("sample", "原句", "https://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), null, "分析", "原创示例", 4, true, NOW));
        assertInvalid(() -> new HumorMaterial("sample", "原句", "https://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                "分析", "原创示例", 4, true, 0, NOW, NOW));
        assertInvalid(() -> new HumorMaterial("sample", "原句", "https://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                "分析", "原创示例", 4, true, 1, NOW, NOW.minusSeconds(1)));
        assertInvalid(() -> HumorMaterial.create("sample", "原句", "https://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                "分析", "原创示例", 4, true, null));
    }

    @Test
    void laterRevisionCannotMoveUpdatedTimeBackwards() {
        HumorMaterial updated = sample().revise("第二版", "https://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                "分析", "原创示例", 4, true, NOW.plusSeconds(60));

        assertInvalid(() -> updated.revise("第三版", "https://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                "分析", "原创示例", 4, true, NOW.plusSeconds(30)));
    }

    private static HumorMaterial sample() {
        return HumorMaterial.create("sample", "原句", "https://example.com/source", "来源",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                "分析", "原创示例", 4, true, NOW);
    }

    private static void assertInvalid(Runnable action) {
        assertEquals(DomainErrorCode.INVALID_CONTENT, assertThrows(DomainException.class, action::run).code());
    }
}
