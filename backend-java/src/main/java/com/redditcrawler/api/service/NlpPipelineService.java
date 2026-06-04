package com.redditcrawler.api.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * NLP pipeline service providing TF-IDF keyword extraction
 * and basic LDA-style topic modeling on crawled Reddit posts.
 *
 * Pure-Java algorithms -- no external ML libraries required.
 */
@Service
public class NlpPipelineService {

    /* ------------------------------------------------------------------ */
    /* CONFIG                                                             */
    /* ------------------------------------------------------------------ */

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "and", "but", "or", "nor", "so", "yet", "both",
            "either", "neither", "not", "each", "every", "all", "any", "few",
            "more", "most", "other", "some", "such", "no", "only", "own", "same",
            "than", "too", "very", "just", "about", "above", "if", "it", "its",
            "my", "that", "this", "they", "we", "who", "what", "where", "when",
            "which", "while", "how", "why", "here", "there", "then", "once",
            "also", "get", "got", "like", "make", "one", "two", "many",
            "well", "even", "still", "long", "back", "right", "much", "new"
    );

    /* ------------------------------------------------------------------ */
    /* PUBLIC API                                                         */
    /* ------------------------------------------------------------------ */

    // -- corpus helper: collect all completed posts into docs              --

    private List<RedditCrawlerService.PostDTO> collectPosts() {
        // Caller will use crawlerService.getAllJobs() directly; this is just a convenience
        return null;
    }

    /**
     * Corpus-wide TF-IDF keyword extraction.
     */
    public List<Map<String, Object>> tfidfKeywords(List<Map<String, String>> posts, int topN) {
        if (posts == null || posts.isEmpty()) return Collections.emptyList();

        List<List<String>> docs = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>(); // doc frequency
        Set<String> vocab = new TreeSet<>();

        for (Map<String, String> post : posts) {
            List<String> tokens = tokenize(post.get("title") + " " + post.get("body"));
            docs.add(tokens);
            SortedSet<String> uniq = new TreeSet<>(tokens);
            for (String w : uniq) df.merge(w, 1, Integer::sum);
            vocab.addAll(uniq);
        }

        int N = docs.size();
        Map<String, Double> avgTfidf = new LinkedHashMap<>();
        Map<String, Integer> totalFreq = new HashMap<>();

        for (List<String> docTokens : docs) {
            int len = docTokens.isEmpty() ? 1 : docTokens.size();
            Map<String, Integer> tf = wordFreq(docTokens);
            for (String term : vocab) {
                int cnt = tf.getOrDefault(term, 0);
                if (cnt == 0) continue;
                double idf = Math.log((double) N / df.getOrDefault(term, 1));
                avgTfidf.merge(term, ((double) cnt / len) * idf, Double::sum);
                totalFreq.merge(term, cnt, Integer::sum);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String term : vocab) {
            double avg = avgTfidf.getOrDefault(term, 0.0);
            if (avg < 1e-6) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("word", term);
            e.put("tfidf", Math.round(avg * 10000.0) / 10000.0);
            e.put("df", df.getOrDefault(term, 0));
            e.put("frequency", totalFreq.get(term));
            result.add(e);
        }

        result.sort((a, b) -> Double.compare(
                ((Number) b.get("tfidf")).doubleValue(),
                ((Number) a.get("tfidf")).doubleValue()));

        return result.size() > topN ? result.subList(0, Math.min(topN, result.size())) : result;
    }

    /**
     * TF-IDF for a single document identified in-corpus by index.
     */
    public List<Map<String, Object>> tfidfForDocument(List<Map<String, String>> corpus,
                                                       int docIndex, int topN) {
        if (corpus == null || corpus.isEmpty() || docIndex < 0 || docIndex >= corpus.size()) return Collections.emptyList();

        List<List<String>> docs = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        Set<String> vocab = new TreeSet<>();

        for (Map<String, String> post : corpus) {
            List<String> tokens = tokenize(post.get("title") + " " + post.get("body"));
            docs.add(tokens);
            SortedSet<String> uniq = new TreeSet<>(tokens);
            for (String w : uniq) df.merge(w, 1, Integer::sum);
            vocab.addAll(uniq);
        }

        int N = docs.size();
        int docLen = docs.get(docIndex).isEmpty() ? 1 : docs.get(docIndex).size();
        Map<String, Integer> tf = wordFreq(docs.get(docIndex));

        List<Map<String, Object>> result = new ArrayList<>();
        for (String term : vocab) {
            int cnt = tf.getOrDefault(term, 0);
            if (cnt == 0) continue;
            double idf = Math.log((double) N / df.getOrDefault(term, 1));
            double tfidf = ((double) cnt / docLen) * idf;

            Map<String, Object> e = new LinkedHashMap<>();
            e.put("word", term);
            e.put("tfidf", Math.round(tfidf * 10000.0) / 10000.0);
            e.put("df", df.getOrDefault(term, 0));
            result.add(e);
        }

        result.sort((a, b) -> Double.compare(
                ((Number) b.get("tfidf")).doubleValue(),
                ((Number) a.get("tfidf")).doubleValue()));

        return result.size() > topN ? result.subList(0, Math.min(topN, result.size())) : result;
    }

    /**
     * LDA topic modeling via Gibbs sampling with Dirichlet priors (alpha=50, beta=0.01).
     */
    public List<Map<String, Object>> ldaTopics(List<Map<String, String>> posts, int k) {
        if (posts == null || posts.isEmpty()) return Collections.emptyList();

        // Tokenize
        List<List<String>> docs = new ArrayList<>();
        for (Map<String, String> post : posts) {
            docs.add(tokenize(post.get("title") + " " + post.get("body")));
        }
        if (docs.stream().allMatch(List::isEmpty)) return Collections.emptyList();

        // Vocabulary
        Set<String> vocab = new TreeSet<>();
        for (List<String> doc : docs) vocab.addAll(doc);
        List<String> vocabList = new ArrayList<>(vocab);
        int V = vocab.size();
        if (V == 0) return Collections.emptyList();

        final Map<String, Integer> w2idx = new HashMap<>();
        for (int i = 0; i < vocabList.size(); i++) w2idx.put(vocabList.get(i), i);

        int D = docs.size();
        k = Math.min(k, Math.max(2, Math.min(D / 5, V / 10)));
        if (k < 2) k = 2;

        // LDA parameters
        final int alpha = 50;
        final double beta = 0.01;
        final int iters = 300;

        Random rng = new Random(42);
        int[][] nDt   = new int[D][k];  // doc-topic counts
        int[]   nT    = new int[k];     // topic total docs
        int[][] nTw   = new int[k][V];  // topic-word counts
        int[]   nW    = new int[V];     // word global count across topics
        int[][] assgn = new int[D][];   // per-token assignment

        // Initialize
        for (int d = 0; d < D; d++) {
            List<String> tokens = docs.get(d);
            assgn[d] = new int[tokens.size()];
            for (int j = 0; j < tokens.size(); j++) {
                int wi = w2idx.getOrDefault(tokens.get(j), -1);
                if (wi < 0) continue;
                int t = rng.nextInt(k);
                assgn[d][j] = t; nDt[d][t]++; nT[t]++; nTw[t][wi]++; nW[wi]++;
            }
        }

        // Gibbs sampling
        for (int iter = 0; iter < iters; iter++) {
            for (int d = 0; d < D; d++) {
                int[] da = assgn[d];
                for (int j = 0; j < da.length; j++) {
                    int wi = w2idx.getOrDefault(docs.get(d).get(j), -1);
                    if (wi < 0) continue;
                    int tOld = da[j];

                    // Remove old
                    nDt[d][tOld]--; nT[tOld]--; nTw[tOld][wi]--; nW[wi]--;

                    // Compute theta
                    double[] theta = new double[k];
                    for (int t = 0; t < k; t++) {
                        if (nT[t] == 0) continue;
                        theta[t] = ((double)(nDt[d][t]+alpha)/(nT[t]+D*alpha))
                                  *((double)(nTw[t][wi]+beta)/(nW[wi]+V*beta));
                    }
                    double sum = Arrays.stream(theta).sum();
                    if (sum > 1e-12) for (int t=0; t<k; t++) theta[t] /= sum;

                    // Sample
                    double r = rng.nextDouble(), cum = 0;
                    int tn = k - 1;
                    for (tn = 0; tn < k; tn++) { cum += theta[tn]; if (r <= cum) break; }

                    // Add new
                    da[j] = tn; nDt[d][tn]++; nT[tn]++; nTw[tn][wi]++; nW[wi]++;
                }
            }
        }

        // Build result: top words per topic
        List<Map<String, Object>> result = new ArrayList<>();
        for (int t = 0; t < k; t++) {
            List<int[]> scores = new ArrayList<>();
            for (String word : vocabList) {
                int wi = w2idx.getOrDefault(word, -1);
                if (nTw[t][wi] > 1) {
                    double prob = nTw[t][wi] / ((double)nT[t] + k * beta);
                    scores.add(new int[]{wi, (int)(prob * 10000)});
                }
            }
            scores.sort((a, b) -> Integer.compare(b[1], a[1]));

            List<Map<String, Object>> topWords = new ArrayList<>();
            for (int i = 0; i < Math.min(20, scores.size()); i++) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("word", vocabList.get(scores.get(i)[0]));
                e.put("probability", scores.get(i)[1] / 10000.0);
                topWords.add(e);
            }

            Map<String, Object> topicEntry = new LinkedHashMap<>();
            topicEntry.put("topicId", t);
            topicEntry.put("topWords", topWords);
            topicEntry.put("docCount", nT[t]);
            result.add(topicEntry);
        }

        result.sort((a, b) -> Integer.compare(
                ((Number) b.get("docCount")).intValue(),
                ((Number) a.get("docCount")).intValue()));

        return result;
    }

    /**
     * Combined LDA clustering + per-cluster TF-IDF ranking.
     */
    public List<Map<String, Object>> clusteredIdas(List<Map<String, String>> posts, int k) {
        if (posts == null || posts.isEmpty()) return Collections.emptyList();

        List<List<String>> docs = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        Set<String> vocab = new TreeSet<>();

        for (Map<String, String> post : posts) {
            List<String> tokens = tokenize(post.get("title") + " " + post.get("body"));
            docs.add(tokens);
            SortedSet<String> uniq = new TreeSet<>(tokens);
            for (String w : uniq) df.merge(w, 1, Integer::sum);
            vocab.addAll(uniq);
        }

        if (docs.stream().allMatch(List::isEmpty)) return Collections.emptyList();

        int D = docs.size();
        int V = vocab.size();
        List<String> vocabList = new ArrayList<>(vocab);
        k = Math.min(k, Math.max(2, Math.min(D / 5, V / 10)));
        if (k < 2) k = 2;

        final Map<String, Integer> w2idx;
        w2idx = new HashMap<>();
        for (int i = 0; i < vocabList.size(); i++) w2idx.put(vocabList.get(i), i);

        // LDA with seed
        Random rng = new Random(123);
        final int alpha = 50, iterations = 200;
        final double beta = 0.01;

        int[][] nDt = new int[D][k], nTw = new int[k][V];
        int[] nT = new int[k], nW = new int[V];
        int[][] da = new int[D][];

        for (int d = 0; d < D; d++) {
            List<String> toks = docs.get(d);
            da[d] = new int[toks.size()];
            for (int j = 0; j < toks.size(); j++) {
                int wi = w2idx.getOrDefault(toks.get(j), -1);
                if (wi < 0) continue;
                int t = rng.nextInt(k);
                da[d][j] = t; nDt[d][t]++; nT[t]++; nTw[t][wi]++; nW[wi]++;
            }
        }

        for (int iter = 0; iter < iterations; iter++) {
            for (int d = 0; d < D; d++) {
                int[] tA = da[d];
                for (int j = 0; j < tA.length; j++) {
                    int wi = w2idx.getOrDefault(docs.get(d).get(j), -1);
                    if (wi < 0) continue;
                    int tO = tA[j];
                    nDt[d][tO]--; nT[tO]--; nTw[tO][wi]--; nW[wi]--;

                    double[] th = new double[k];
                    for (int t = 0; t < k; t++) {
                        if (nT[t] == 0) continue;
                        th[t] = (((double)(nDt[d][t]+alpha)/(nT[t]+D*alpha))
                                *((double)(nTw[t][wi]+beta)/(nW[wi]+V*beta)));
                    }
                    double s = Arrays.stream(th).sum();
                    if (s > 1e-12) for (int t=0; t<k; t++) th[t] /= s;

                    double r = rng.nextDouble(), c = 0;
                    int tn = k-1;
                    for (tn=0; tn<k; tn++) { c += th[tn]; if(r<=c) break; }

                    tA[j] = tn; nDt[d][tn]++; nT[tn]++; nTw[tn][wi]++; nW[wi]++;
                }
            }
        }

        // Cluster by majority vote
        int[] cluster = new int[D];
        for (int d = 0; d < D; d++) {
            int bestT = 0, bestC = -1;
            for (int t = 0; t < k; t++) {
                if (nDt[d][t] > bestC) { bestC = nDt[d][t]; bestT = t; }
            }
            cluster[d] = bestT;
        }

        // Per-cluster stats
        List<Map<String, Object>> result = new ArrayList<>();
        for (int t = 0; t < k; t++) {
            List<List<String>> cDocs = new ArrayList<>();
            int n = 0;
            for (int d = 0; d < D; d++) if (cluster[d] == t) { cDocs.add(docs.get(d)); n++; }
            if (n == 0) continue;

            Map<String, Integer> tf = new HashMap<>();
            for (List<String> toks : cDocs)
                for (String w : toks) {
                    if (w.length() < 3 || STOP_WORDS.contains(w)) continue;
                    tf.merge(w, 1, Integer::sum);
                }

            final int clusterSize = n;
            List<Map<String, Object>> topWords = new ArrayList<>();
            tf.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(15)
                    .forEach(e -> {
                        double idf = Math.log((double) clusterSize / df.getOrDefault(e.getKey(), 1));
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("word", e.getKey());
                        entry.put("count", e.getValue());
                        entry.put("idfBoost", Math.round(idf * 10000.0) / 10000.0);
                        topWords.add(entry);
                    });

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("topicId", t);
            entry.put("docCount", nT[t]);
            entry.put("topWords", topWords);
            result.add(entry);
        }

        return result;
    }

    /* ------------------------------------------------------------------ */
    /* HELPERS                                                            */
    /* ------------------------------------------------------------------ */

    /** Count frequency of each word in a list. */
    private Map<String, Integer> wordFreq(List<String> tokens) {
        Map<String, Integer> freq = new HashMap<>();
        for (String t : tokens) freq.merge(t, 1, Integer::sum);
        return freq;
    }

    /** Tokenize text: lowercase, split on non-alphanumeric, filter stop-words and short/bare-number tokens. */
    protected List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        String[] raw = text.split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String tok : raw) {
            if (tok.length() < 3) continue;
            if (STOP_WORDS.contains(tok)) continue;
            if (tok.matches("^\\d+$")) continue; // skip bare numbers
            tokens.add(tok);
        }
        return tokens;
    }
}
