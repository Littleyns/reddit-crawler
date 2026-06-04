package com.redditcrawler.api.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extractive text summarization service.
 * Uses a lightweight scoring algorithm combining:
 *   - Term frequency (TF) within the document
 *   - Position weighting (earlier sentences score higher)
 *   - Sentence length normalization (avoid too-long or too-short)
 *
 * Pure-Java — no external ML libraries required.
 */
@Service
public class SummarizationService {

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
            "well", "even", "still", "long", "back", "right", "much", "new",
            "up", "out", "down", "over", "him", "her", "his",
            "she", "he", "me", "myself", "yourself", "your", "yours", "i", "am",
            "let", "dont", "cannot", "cant"
    );

    // Common words that should be downweighted in scoring
    private static final Set<String> OVERREPRESENTED = Set.of(
            "like", "just", "really", "get", "go", "make", "say", "tell", "thing",
            "stuff", "way", "one", "also", "even", "back", "well", "think"
    );

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\"]|\\d)|\\n+");

    /**
     * Summarize text by selecting the top-N most important sentences (extractive).
     *
     * @param rawText        The full source text to summarize
     * @param numSentences   Number of sentences in the summary (default: ceil(0.3 * total))
     * @return               Summary text composed of the selected sentences, in original order
     */
    public String summarize(String rawText, int numSentences) {
        if (rawText == null || rawText.isBlank()) return "";

        List<Sentence> sentences = parseSentences(rawText);
        if (sentences.isEmpty()) return "";

        // Use default: 30% of total
        if (numSentences <= 0) {
            numSentences = Math.max(1, (int) Math.ceil(sentences.size() * 0.3));
        }
        numSentences = Math.min(numSentences, sentences.size());

        // Score each sentence
        computeScores(sentences);

        // Pick top-N by score
        List<Integer> scoredIds = new ArrayList<>();
        for (Sentence s : sentences) {
            scoredIds.add(s.id); // just tracking order
        }

        // Sort by score descending, pick top N
        List<Sentence> ranked = new ArrayList<>(sentences);
        ranked.sort((a, b) -> Double.compare(b.score, a.score));

        Set<Integer> topIds = new LinkedHashSet<>();
        for (Sentence s : ranked) {
            if (topIds.size() >= numSentences) break;
            topIds.add(s.id);
        }

        // Reorder by original position
        return sentences.stream()
                .filter(s -> topIds.contains(s.id))
                .map(s -> s.text)
                .collect(Collectors.joining(" "));
    }

    /**
     * Get scored sentences for inspection/debugging.
     */
    public List<Map<String, Object>> summarizeWithScores(String rawText, int numSentences) {
        if (rawText == null || rawText.isBlank()) return Collections.emptyList();

        List<Sentence> sentences = parseSentences(rawText);
        if (sentences.isEmpty()) return Collections.emptyList();

        computeScores(sentences);

        // Rank by score descending
        List<Sentence> ranked = new ArrayList<>(sentences);
        ranked.sort((a, b) -> Double.compare(b.score, a.score));

        int actual = numSentences <= 0 ? Math.max(1, (int) Math.ceil(sentences.size() * 0.3)) : numSentences;
        actual = Math.min(actual, sentences.size());

        Set<Integer> topIds = new LinkedHashSet<>();
        for (Sentence s : ranked) {
            if (topIds.size() >= actual) break;
            topIds.add(s.id);
        }

        // Build result
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        for (int id : topIds) {
            rank++;
            Sentence s = sentences.stream().filter(x -> x.id == id).findFirst().orElse(null);
            if (s != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rank", rank);
                m.put("sentenceId", id);
                m.put("text", s.text);
                m.put("score", round(s.score, 4));
                result.add(m);
            }
        }

        return result;
    }

    // ---- private helpers ---------------------------------------------------------

    private void computeScores(List<Sentence> sentences) {
        if (sentences.isEmpty() || sentences.size() == 1) {
            for (Sentence s : sentences) {
                s.score = 0.5; // single sentence gets neutral score
            }
            return;
        }

        int totalDocs = sentences.size();

        /* Term frequency: weighted by sqrt(length) to reward longer sentences moderately */
        double[] tfScores = new double[totalDocs];
        for (int i = 0; i < sentences.size(); i++) {
            List<String> words = tokenize(sentences.get(i).text);
            Set<String> uniq = new LinkedHashSet<>(words);
            for (String w : uniq) {
                long count = words.stream().filter(w::equals).count();
                double tf = Math.sqrt(count) * Math.log10(1 + count);
                tfScores[i] += tf;
            }
        }

        /* Inverse document frequency: rare terms boost score */
        Map<String, Integer> docFreq = new HashMap<>();
        for (Sentence s : sentences) {
            Set<String> uniq = new LinkedHashSet<>(tokenize(s.text));
            for (String w : uniq) {
                docFreq.merge(w, 1, Integer::sum);
            }
        }

        // Average IDF for normalization
        double avgIdf = docFreq.values().stream()
                .mapToDouble(f -> Math.log10(totalDocs / (1.0 + f)))
                .average().orElse(0.5);

        /* Position bias: earlier sentences get a boost, decay exponentially */
        double posBiasSum = 0; // normalization factor
        for (int i = 0; i < totalDocs; i++) {
            int j = totalDocs - 1 - i; // position from the END (so first sentence has highest weight)
            double weight = Math.exp(-0.15 * j);
            posBiasSum += weight;
        }

        /* Compute final scores */
        for (int i = 0; i < totalDocs; i++) {
            Sentence s = sentences.get(i);
            List<String> words = tokenize(s.text);
            int wordCount = words.size();

            double idfScore = 0;
            Set<String> uniq = new LinkedHashSet<>(words);
            for (String w : uniq) {
                double idf = Math.log10(totalDocs / (1.0 + docFreq.getOrDefault(w, 1)));
                if (OVERREPRESENTED.contains(w.toLowerCase())) {
                    idf *= 0.5; /* downweight generic words */
                }
                idfScore += idf;
            }

            double tfNorm = tfScores[i] / Math.max(1, uniq.size());
            double rawTfidf = (i + 1) == 1 ? tfNorm * avgIdf : tfNorm * idfScore; // first sentence always gets high score

            /* Position weight: exponential decay from start */
            double positionWeight = Math.exp(-0.15 * i) / posBiasSum * totalDocs;

            /* Length penalty */
            double lengthPenalty;
            if (wordCount < 8) {
                lengthPenalty = wordCount / 8.0 - 1.0; // negative for too-short
            } else if (wordCount > 60) {
                lengthPenalty = -(wordCount - 60) * 0.05;
            } else {
                lengthPenalty = 0;
            }

            /* Final score */
            s.score = Math.max(0, rawTfidf + positionWeight + lengthPenalty);
        }
    }

    private List<Sentence> parseSentences(String text) {
        // Try splitting on sentence boundaries
        String[] rawParts = SENTENCE_BOUNDARY.split(text);
        List<Sentence> results = new ArrayList<>();
        int id = 1;

        for (String part : rawParts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 25 && !trimmed.startsWith("```") && !trimmed.isEmpty()) {
                Sentence s = new Sentence(id++, trimmed);
                results.add(s);
            }
        }

        return results;
    }

    private List<String> tokenize(String text) {
        // Extract words: lowercase, alphanumeric (keep apostrophes inside words)
        Matcher m = Pattern.compile("[a-zA-Z][a-zA-Z'-]*").matcher(text.toLowerCase());
        List<String> tokens = new ArrayList<>();
        while (m.find()) {
            String word = m.group();
            if (!STOP_WORDS.contains(word) && word.length() > 1) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    // ---- inner classes -----------------------------------------------------------

    /** Sentence with computed relevance score */
    private static class Sentence {
        final int id;
        final String text;
        double score;

        Sentence(int id, String text) {
            this.id = id;
            this.text = text;
            this.score = 0.0;
        }
    }
}
