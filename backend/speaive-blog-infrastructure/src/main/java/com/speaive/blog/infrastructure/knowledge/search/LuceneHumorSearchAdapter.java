package com.speaive.blog.infrastructure.knowledge.search;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.knowledge.HumorSearchHit;
import com.speaive.blog.application.port.out.knowledge.HumorSearchPort;
import com.speaive.blog.application.port.out.knowledge.HumorSearchQuery;
import com.speaive.blog.application.port.out.persistence.HumorMaterialRepository;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * A small, disposable BM25 projection of PostgreSQL material. Every lookup compares complete immutable rows,
 * so enablement and edits take effect on the next lookup without relying on timestamps or a background refresh.
 */
public final class LuceneHumorSearchAdapter implements HumorSearchPort, AutoCloseable {
    private static final int MAX_HITS = 20;
    private static final int MAX_QUERY_TERMS = 64;
    private static final Map<String, Float> FIELD_BOOSTS = Map.of(
            "topics", 3.0f, "excerpt", 2.0f, "analysis", 1.0f, "adaptation", 0.8f, "source_title", 0.3f);

    private final HumorMaterialRepository materials;
    // Lucene smartcn's bundled default stop list contains punctuation, not Chinese negation words.
    private final SmartChineseAnalyzer analyzer = new SmartChineseAnalyzer();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private IndexSnapshot current;
    private boolean closed;

    public LuceneHumorSearchAdapter(HumorMaterialRepository materials) {
        this.materials = Objects.requireNonNull(materials, "materials");
    }

    @Override
    public List<HumorSearchHit> search(HumorSearchQuery query) {
        Objects.requireNonNull(query, "query");
        // Downgrade after refreshing: readers never observe a partial index or a reader being closed beneath them.
        lifecycle.writeLock().lock();
        try {
            ensureOpen();
            if (query.text().isBlank()) {
                return List.of();
            }
            var rows = materials.findAll().stream().sorted(Comparator.comparing(HumorMaterial::id)).toList();
            if (current == null || !current.rows().equals(rows)) {
                IndexSnapshot replacement = build(rows);
                IndexSnapshot previous = current;
                current = replacement;
                if (previous != null) {
                    previous.close();
                }
            }
            lifecycle.readLock().lock();
        } catch (IOException error) {
            throw storage(error);
        } finally {
            lifecycle.writeLock().unlock();
        }

        try {
            Query searchQuery = buildQuery(query);
            if (searchQuery == null || current.reader().numDocs() == 0) {
                return List.of();
            }
            var result = current.searcher().search(searchQuery, Math.min(MAX_HITS, query.limit()));
            var hits = new ArrayList<HumorSearchHit>(result.scoreDocs.length);
            var storedFields = current.searcher().storedFields();
            for (var scored : result.scoreDocs) {
                if (scored.score > 0) {
                    String id = storedFields.document(scored.doc).get("id");
                    hits.add(new HumorSearchHit(current.byId().get(id), scored.score));
                }
            }
            return List.copyOf(hits);
        } catch (IOException error) {
            throw storage(error);
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    private IndexSnapshot build(List<HumorMaterial> rows) throws IOException {
        var directory = new ByteBuffersDirectory();
        DirectoryReader reader = null;
        try {
            var settings = new IndexWriterConfig(analyzer).setSimilarity(new BM25Similarity());
            try (var writer = new IndexWriter(directory, settings)) {
                for (HumorMaterial material : rows) {
                    var document = new Document();
                    document.add(new StringField("id", material.id(), Field.Store.YES));
                    document.add(new StringField("enabled", Boolean.toString(material.enabled()), Field.Store.NO));
                    document.add(new StringField("mechanism", material.mechanism().name(), Field.Store.NO));
                    document.add(new TextField("topics", String.join(" ", material.topics()), Field.Store.NO));
                    document.add(new TextField("excerpt", material.excerpt(), Field.Store.NO));
                    document.add(new TextField("analysis", material.analysis(), Field.Store.NO));
                    document.add(new TextField("adaptation", material.adaptation(), Field.Store.NO));
                    document.add(new TextField("source_title", material.sourceTitle(), Field.Store.NO));
                    writer.addDocument(document);
                }
            }
            reader = DirectoryReader.open(directory);
            var searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            var byId = new LinkedHashMap<String, HumorMaterial>();
            rows.forEach(material -> byId.put(material.id(), material));
            return new IndexSnapshot(rows, Map.copyOf(byId), directory, reader, searcher);
        } catch (IOException | RuntimeException error) {
            if (reader != null) {
                try { reader.close(); } catch (IOException closing) { error.addSuppressed(closing); }
            }
            try { directory.close(); } catch (IOException closing) { error.addSuppressed(closing); }
            throw error;
        }
    }

    private Query buildQuery(HumorSearchQuery query) throws IOException {
        var terms = new LinkedHashSet<String>();
        try (var stream = analyzer.tokenStream("topics", query.text())) {
            var term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && terms.size() < MAX_QUERY_TERMS) {
                terms.add(term.toString());
            }
            stream.end();
        }
        if (terms.isEmpty()) {
            return null;
        }
        var words = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
        for (String term : terms) {
            var fields = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
            FIELD_BOOSTS.forEach((field, boost) -> fields.add(
                    new BoostQuery(new TermQuery(new Term(field, term)), boost), BooleanClause.Occur.SHOULD));
            words.add(fields.build(), BooleanClause.Occur.SHOULD);
        }
        var filtered = new BooleanQuery.Builder()
                .add(words.build(), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term("enabled", "true")), BooleanClause.Occur.FILTER);
        if (query.mechanism() != null) {
            filtered.add(new TermQuery(new Term("mechanism", query.mechanism().name())), BooleanClause.Occur.FILTER);
        }
        return filtered.build();
    }

    @Override
    public void close() {
        lifecycle.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (current != null) {
                    current.close();
                    current = null;
                }
            } finally {
                analyzer.close();
            }
        } catch (IOException error) {
            throw storage(error);
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Humor search index is closed");
        }
    }

    private static BlogException storage(IOException error) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, "幽默素材索引暂时不可用", error);
    }

    private record IndexSnapshot(List<HumorMaterial> rows, Map<String, HumorMaterial> byId,
            ByteBuffersDirectory directory, DirectoryReader reader, IndexSearcher searcher) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                reader.close();
            } finally {
                directory.close();
            }
        }
    }
}
