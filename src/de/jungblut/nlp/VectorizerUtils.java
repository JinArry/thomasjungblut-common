package de.jungblut.nlp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.util.FastMath;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

import de.jungblut.datastructure.ArrayUtils;
import de.jungblut.math.DoubleVector;
import de.jungblut.math.named.NamedDoubleVector;
import de.jungblut.math.sparse.SparseDoubleVector;
import de.jungblut.nlp.model.Pair;
import de.jungblut.nlp.model.ReferencedContext;

/**
 * Vectorizing utility for basic tf-idf and wordcount vectorizing of
 * tokens/strings. It can also build inverted indices and dictionaries.
 * 
 * @author thomas.jungblut
 * 
 */
public final class VectorizerUtils {

  private VectorizerUtils() {
    throw new IllegalAccessError();
  }

  /**
   * Builds a sorted dictionary of tokens from a list of (tokenized) documents.
   * It treats tokens that are contained in at least 90% of all documents as
   * spam, they won't be included in the final dictionary.
   * 
   * @param tokenizedDocuments the documents that are already tokenized.
   * @return a sorted String array with tokens in it.
   */
  public static String[] buildDictionary(List<String[]> tokenizedDocuments) {
    return buildDictionary(tokenizedDocuments, 0.9f, 0);
  }

  /**
   * Builds a sorted dictionary of tokens from a list of (tokenized) documents.
   * It treats tokens that are contained in at least "stopWordPercentage"% of
   * all documents as spam, they won't be included in the final dictionary.
   * 
   * @param tokenizedDocuments the documents that are the base for the
   *          dictionary.
   * @param stopWordPercentage the percentage of how many documents must contain
   *          a token until it can be classified as spam. Ranges between 0f and
   *          1f, where 0f will actually return an empty dictionary.
   * @param minFrequency the minimum frequency a token must occur globally.
   *          (strict greater than supplied value)
   * @return a sorted String array with tokens in it.
   */
  public static String[] buildDictionary(List<String[]> tokenizedDocuments,
      float stopWordPercentage, int minFrequency) {
    Preconditions.checkArgument(stopWordPercentage >= 0f
        && stopWordPercentage <= 1f,
        "The provided stop word percentage is not between 0 and 1: "
            + stopWordPercentage);
    HashMultiset<String> set = HashMultiset.create();

    for (String[] doc : tokenizedDocuments) {
      // deduplication, because we want to measure how often a token is in a
      // doc, so we have to get distinct tokens in a document.
      final ArrayList<String> deduplicate = ArrayUtils.deduplicate(doc);
      for (String token : deduplicate) {
        set.add(token);
      }
    }
    final int threshold = (int) (stopWordPercentage * tokenizedDocuments.size());
    List<String> toRemove = new ArrayList<>();
    // now remove the spam
    for (Entry<String> entry : set.entrySet()) {
      if (entry.getCount() > threshold || entry.getCount() < minFrequency) {
        toRemove.add(entry.getElement());
      }
    }

    set.removeAll(toRemove);

    Set<String> elementSet = set.elementSet();
    String[] array = elementSet.toArray(new String[elementSet.size()]);
    elementSet = null;
    set = null;
    Arrays.sort(array);
    return array;
  }

  /**
   * Builds an inverted index as multi map.
   * 
   * @param tokenizedDocuments the documents to index, already tokenized.
   * @param dictionary the dictionary of words that should be used to build this
   *          index.
   * @return a {@link HashMultimap} that contains a set of integers (index of
   *         the documents in the given input list) mapped by a token that was
   *         contained in the documents.
   */
  public static HashMultimap<String, Integer> buildInvertedIndexMap(
      List<String[]> tokenizedDocuments, String[] dictionary) {
    HashMultimap<String, Integer> indexMap = HashMultimap.create();
    for (int i = 0; i < tokenizedDocuments.size(); i++) {
      String[] tokens = tokenizedDocuments.get(i);
      for (String token : tokens) {
        // check if we have the word in our dictionary
        if (Arrays.binarySearch(dictionary, token) >= 0) {
          indexMap.put(token, i);
        }
      }
    }
    return indexMap;
  }

  /**
   * Builds an inverted index based on the given dictionary, adds just the
   * document index mappings to it.
   * 
   * @param tokenizedDocuments the documents to index, already tokenized.
   * @param dictionary the dictionary of words that should be used to build this
   *          index.
   * @return a two dimensional integer array, that contains the document ids
   *         (index in the given document list) on the same index that the
   *         dictionary maps the token.
   */
  public static int[][] buildInvertedIndexArray(
      List<String[]> tokenizedDocuments, String[] dictionary) {
    HashMultimap<String, Integer> invertedIndex = buildInvertedIndexMap(
        tokenizedDocuments, dictionary);
    int[][] docs = new int[dictionary.length][];

    for (int i = 0; i < dictionary.length; i++) {
      Set<Integer> set = invertedIndex.get(dictionary[i]);
      docs[i] = ArrayUtils
          .toPrimitiveArray(set.toArray(new Integer[set.size()]));
    }

    return docs;
  }

  /**
   * Builds an inverted index document count based on the given dictionary, so
   * at each dimension of the returned array, there is a count of how many
   * documents contained that document.
   * 
   * @param tokenizedDocuments the documents to index, already tokenized.
   * @param dictionary the dictionary of words that should be used to build this
   *          index.
   * @return a one dimensional integer array, that contains the number of
   *         documents on the same index that the dictionary maps the token.
   */
  public static int[] buildInvertedIndexDocumentCount(
      List<String[]> tokenizedDocuments, String[] dictionary) {
    HashMultimap<String, Integer> invertedIndex = buildInvertedIndexMap(
        tokenizedDocuments, dictionary);
    int[] docs = new int[dictionary.length];

    for (int i = 0; i < dictionary.length; i++) {
      Set<Integer> set = invertedIndex.get(dictionary[i]);
      docs[i] = set.size();
    }

    return docs;
  }

  /**
   * Vectorizes a given list of documents. Each vector will have the dimension
   * of how many words are in the build dictionary, each word will have its own
   * mapping in the vector. The value at a certain index (determined by the
   * position in the dictionary) will be the frequncy of the word in the
   * document.
   * 
   * @param tokenizedDocuments the array of documents.
   * @return a list of sparse vectors, representing the documents as vectors
   *         based on word frequency.
   */
  public static List<DoubleVector> wordFrequencyVectorize(String[]... vars) {
    return wordFrequencyVectorize(Arrays.asList(vars));
  }

  /**
   * Vectorizes a given list of documents. Each vector will have the dimension
   * of how many words are in the build dictionary, each word will have its own
   * mapping in the vector. The value at a certain index (determined by the
   * position in the dictionary) will be the frequncy of the word in the
   * document.
   * 
   * @param tokenizedDocuments the list of documents.
   * @return a list of sparse vectors, representing the documents as vectors
   *         based on word frequency.
   */
  public static List<DoubleVector> wordFrequencyVectorize(
      List<String[]> tokenizedDocuments) {
    return wordFrequencyVectorize(tokenizedDocuments,
        buildDictionary(tokenizedDocuments));
  }

  /**
   * Vectorizes a given list of documents and a dictionary. Each vector will
   * have the dimension of how many words are in the dictionary, each word will
   * have its own mapping in the vector. The value at a certain index
   * (determined by the position in the dictionary) will be the frequncy of the
   * word in the document.
   * 
   * @param tokenizedDocuments the list of documents.
   * @param dictionary the dictionary, must be sorted.
   * @return a list of sparse vectors, representing the documents as vectors
   *         based on word frequency.
   */
  public static List<DoubleVector> wordFrequencyVectorize(
      List<String[]> tokenizedDocuments, String[] dictionary) {

    List<DoubleVector> vectorList = new ArrayList<>(tokenizedDocuments.size());
    for (String[] arr : tokenizedDocuments) {
      DoubleVector vector = new SparseDoubleVector(dictionary.length);
      HashMultiset<String> set = HashMultiset.create(Arrays.asList(arr));
      for (String s : arr) {
        int foundIndex = Arrays.binarySearch(dictionary, s);
        // simply ignore tokens we don't know or that are spam
        if (foundIndex >= 0) {
          // the index is equal to its mapped dimension
          vector.set(foundIndex, set.count(s));
        }
      }
      vectorList.add(vector);
    }

    return vectorList;
  }

  /**
   * Vectorizes the given documents by the TF-IDF weighting.
   * 
   * @param tokenizedDocuments the documents to vectorize.
   * @param dictionary the dictionary extracted.
   * @param termDocumentCount the document count per token.
   * @return a list of sparse tf-idf weighted vectors.
   */
  public static List<DoubleVector> tfIdfVectorize(
      List<String[]> tokenizedDocuments, String[] dictionary,
      int[] termDocumentCount) {

    final int numDocuments = tokenizedDocuments.size();
    final int numTokens = dictionary.length;
    List<DoubleVector> list = new ArrayList<>(numDocuments);

    for (String[] document : tokenizedDocuments) {
      DoubleVector vector = new SparseDoubleVector(numTokens);
      HashMultiset<String> termFrequencySet = HashMultiset.create(Arrays
          .asList(document));

      for (String token : document) {
        int index = Arrays.binarySearch(dictionary, token);
        if (index >= 0) {
          double tfIdf = termFrequencySet.count(token)
              * Math.log(numDocuments / (double) termDocumentCount[index]);
          vector.set(index, tfIdf);
        }
      }

      list.add(vector);
    }

    return list;
  }

  /**
   * Vectorizes the given terms in the documents by their context. The values to
   * the corresponding variables is the Pointwise Mutual Information. This is
   * derived by:
   * 
   * <pre>
   * PMI(phrase, feature) = log(p(phrase | feature) / p(phrase))
   * </pre>
   * 
   * where phrase is the referenced token in the document and a feature is the
   * token in the near context of that phrase.
   * 
   * @param tokenizedDocuments the already tokenized documents. Usually start
   *          and end tags should be added.
   * @param dictionary the dictionary that is allowed.
   * @param contextWindow the context window size, meaning how many tokens
   *          before and afterwards should be used to create the context vector.
   * @param compressContext if true, the phrase will only occur once in the list
   *          of vectors, but all the context features will be grouped on this
   *          single phrase vector.
   * @return a list of context vectors, where a single vector encodes a token
   *         (phrase) and the values in that vector is a sparse encoding of
   *         their context. To find the phrase again, the vectors are named with
   *         the token (just a reference to the dictionary's entry).
   */
  public static List<DoubleVector> pointwiseMutualInformationVectorize(
      List<String[]> tokenizedDocuments, String[] dictionary,
      int contextWindow, boolean compressContext) {
    HashMap<String, ReferencedContext<String, String>> compressedMap = Maps
        .newHashMap();
    List<ReferencedContext<String, String>> mappingList = new ArrayList<>();
    // tokenize into context and references
    for (String[] tokens : tokenizedDocuments) {
      for (int i = 0; i < tokens.length; i++) {
        ArrayList<String> ctx = new ArrayList<>();
        for (int cx = 1; cx <= contextWindow; cx++) {
          int forwardIndex = i + cx;
          int backwardIndex = i - cx;
          if (ArrayUtils.isValidIndex(tokens, forwardIndex)) {
            ctx.add(tokens[forwardIndex]);
          }
          if (ArrayUtils.isValidIndex(tokens, backwardIndex)) {
            ctx.add(tokens[backwardIndex]);
          }
        }
        if (compressContext) {
          ReferencedContext<String, String> refCtx = compressedMap
              .get(tokens[i]);
          if (refCtx == null) {
            refCtx = new ReferencedContext<>(tokens[i], new HashSet<>(ctx));
            mappingList.add(refCtx);
            compressedMap.put(tokens[i], refCtx);
          } else {
            refCtx.getContext().addAll(ctx);
          }
        } else {
          ctx.trimToSize();
          mappingList.add(new ReferencedContext<>(tokens[i], ctx));
        }
      }
    }
    // the pairwise counts of phrase and features
    HashMultiset<Pair<String, String>> pairCounter = HashMultiset.create();
    // the probability of a feature
    HashMultiset<String> featureCounter = HashMultiset.create();
    // the probability of a phrase
    HashMultiset<String> phraseCounter = HashMultiset.create();
    for (ReferencedContext<String, String> ref : mappingList) {
      for (String s : ref.getContext()) {
        pairCounter.add(new Pair<>(ref.getReference(), s));
        featureCounter.add(s);
      }
      phraseCounter.add(ref.getReference());
    }

    List<DoubleVector> vectors = new ArrayList<>(mappingList.size());
    for (ReferencedContext<String, String> ref : mappingList) {
      SparseDoubleVector vector = new SparseDoubleVector(dictionary.length);
      final double pPhrase = phraseCounter.count(ref.getReference())
          / (double) phraseCounter.size();
      for (String feature : ref.getContext()) {
        final int index = Arrays.binarySearch(dictionary, feature);
        // ignore features we don't want to know
        if (index >= 0) {
          double pFeature = featureCounter.count(feature)
              / (double) featureCounter.size();
          double conditional = pairCounter.count(new Pair<>(ref.getReference(),
              feature)) / (double) pairCounter.size();
          double p = (conditional / pFeature) / pPhrase;
          double value = FastMath.log(p);
          if (!Double.isNaN(value) && !Double.isInfinite(value)) {
            vector.set(index, value);
          }
        }
      }
      vectors.add(new NamedDoubleVector(ref.getReference(), vector));
    }

    return vectors;
  }

  /**
   * Given a multiset of generic elements we are going to return a list of all
   * the elements, sorted descending by their frequency.
   * 
   * @param set the given multiset.
   * @return a descending sorted list by frequency.
   */
  public static <E> ArrayList<Entry<E>> getMostFrequentItems(Multiset<E> set) {
    return getMostFrequentItems(set, null);
  }

  /**
   * Given a multiset of generic elements we are going to return a list of all
   * the elements, sorted descending by their frequency. Also can apply a filter
   * on the multiset, for example a filter for wordfrequency > 1.
   * 
   * @param set the given multiset.
   * @param filter if not null it filters by the given {@link Predicate}.
   * @return a descending sorted list by frequency.
   */
  public static <E> ArrayList<Entry<E>> getMostFrequentItems(Multiset<E> set,
      Predicate<Entry<E>> filter) {

    ArrayList<Entry<E>> list = Lists.newArrayList(filter == null ? set
        .entrySet() : Iterables.filter(set.entrySet(), filter));
    Collections.sort(list, new Comparator<Entry<E>>() {
      @Override
      public int compare(Entry<E> o1, Entry<E> o2) {
        return Integer.compare(o2.getCount(), o1.getCount());
      }
    });

    return list;
  }

}
