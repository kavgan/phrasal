package edu.stanford.nlp.mt.pt;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.IntegerArrayIndex;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.ProbingIntegerArrayIndex;
import edu.stanford.nlp.mt.util.RawSequence;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.VocabularyFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * A simple phrase table implementation.
 * 
 * @author Daniel Cer
 */
public class FlatPhraseTable<FV> extends AbstractPhraseGenerator<IString, FV>
    implements PhraseTable<IString> {

  public static final String TRIE_INDEX_PROPERTY = "TriePhraseTable";
  public static final boolean TRIE_INDEX = Boolean.parseBoolean(System
      .getProperty(TRIE_INDEX_PROPERTY, "false"));

  private static final int INITIAL_CAPACITY = 50000;
  
  public static final String FIELD_DELIM = "|||";
  public static final String FEATURE_PREFIX = "FPT";

//  public static IntegerArrayIndex sourceIndex;
//  public static IntegerArrayIndex ruleIndex;

  protected IntegerArrayIndex sourceToRuleIndex;
  protected IntegerArrayIndex targetIndex;
  private static final AtomicInteger ruleIdCounter = new AtomicInteger();
  
  private final int minRuleIndex;
  public final String[] scoreNames;
  protected String name;
  public final List<List<IntArrayTranslationOption>> translations;
  
  protected int longestSourcePhrase = -1;
  protected int longestTargetPhrase = -1;

  /**
   * Constructor.
   * 
   * @param phraseFeaturizer
   * @param filename
   * @throws IOException
   */
  public FlatPhraseTable(
      RuleFeaturizer<IString, FV> phraseFeaturizer,
      String filename) throws IOException {
    // default is not to do logarithm on the scores
    this(phraseFeaturizer, filename, false);
  }

  /**
   * Constructor.
   * 
   * @param filename
   * @throws IOException
   */
  public FlatPhraseTable(String filename) throws IOException {
    this(null, filename, false);
  }

  /**
   * Constructor.
   * 
   * @param phraseFeaturizer
   * @param filename
   * @param reverse
   * @throws IOException
   */
  public FlatPhraseTable(
      RuleFeaturizer<IString, FV> phraseFeaturizer,
      String filename, boolean reverse) throws IOException {
    super(phraseFeaturizer);
    File f = new File(filename);
    name = String.format("FlatPhraseTable(%s)", f.getName());
    minRuleIndex = ruleIdCounter.get();
    translations = Generics.newArrayList(INITIAL_CAPACITY);
    sourceToRuleIndex = new ProbingIntegerArrayIndex();
    targetIndex = new ProbingIntegerArrayIndex();
    int countScores = init(f, reverse);
    scoreNames = new String[countScores];
    for (int i = 0; i < countScores; i++) {
      scoreNames[i] = String.format("%s.%d", FEATURE_PREFIX, i);
    }
  }
  
  @Override
  public int size() { return ruleIdCounter.get(); }

  protected void addEntry(Sequence<IString> sourceSequence,
      Sequence<IString> targetSequence, PhraseAlignment alignment,
      float[] scores) {
    VocabularyFactory.getSourceInstance().add(sourceSequence);
    VocabularyFactory.getTargetInstance().add(targetSequence);

    int[] sourceArray = Sequences.toIntArray(sourceSequence);
    int[] targetArray = Sequences.toIntArray(targetSequence);
    int fIndex = sourceToRuleIndex.indexOf(sourceArray, true);
    int eIndex = this.targetIndex.indexOf(targetArray, true);
    
//    int[] foreignInts = Sequences.toIntArray(sourceSequence);
//    int[] translationInts = Sequences.toIntArray(targetSequence);
//    int fIndex = sourceIndex.indexOf(foreignInts, true);
//    int eIndex = ruleIndex.indexOf(translationInts, true);
//    int id = ruleIndex.indexOf(new int[] { fIndex, eIndex }, true);

    if (translations.size() <= fIndex) {
      while (translations.size() <= fIndex)
        translations.add(null);
    }
    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
    if (intTransOpts == null) {
      intTransOpts = Generics.newLinkedList();
      translations.set(fIndex, intTransOpts);
    }
    intTransOpts.add(new IntArrayTranslationOption(ruleIdCounter.getAndIncrement(), 
        targetIndex.get(eIndex), scores, alignment));
  }

  @Override
  public List<String> getFeatureNames() { return Arrays.asList(scoreNames); }

  /**
   * Load the phrase table from file. 
   * 
   * @param f
   * @param reverse
   * @return
   * @throws IOException
   */
  private int init(File f, boolean reverse) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long prePhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    LineNumberReader reader = IOTools.getReaderFromFile(f);
    int numScores = -1;
    for (String line; (line = reader.readLine()) != null;) {
      List<List<String>> fields = StringUtils.splitFieldsFast(line, FlatPhraseTable.FIELD_DELIM);
      
      // The standard format has five fields
      assert fields.size() == 5 : String.format("phrase table line %d has %d fields", 
          reader.getLineNumber(), fields.size());
      Sequence<IString> source = IStrings.toIStringSequence(fields.get(0));
      Sequence<IString> target = IStrings.toIStringSequence(fields.get(1));
//      String sourceConstellation = fields[2];
      String targetConstellation = StringUtils.join(fields.get(3));
      List<String> scoreList = fields.get(4);
      
      if (reverse) {
        Sequence<IString> tmp = source;
        source = target;
        target = tmp;
      }

      // Ensure that all rules in the phrase table have the same number of scores
      if (numScores < 0) {
        numScores = scoreList.size();
      } else if (numScores != scoreList.size()) {
        throw new RuntimeException(
            String
                .format(
                    "Error (line %d): Each entry must have exactly the same number of translation\n"
                        + "scores per line. Prior entries had %d, while the current entry has %d:",
                    reader.getLineNumber(), numScores, scoreList.size()));
      }
      float[] scores;
      try {
        scores = IOTools.stringListToNumeric(scoreList);
      } catch (NumberFormatException e) {
        e.printStackTrace();
        throw new RuntimeException(String.format("Number format error on line %d",
            reader.getLineNumber()));
      }

      if (targetConstellation.equals("")) {
        addEntry(source, target, null, scores);
      } else {
        addEntry(source, target,
            PhraseAlignment.getPhraseAlignment(targetConstellation), scores);
      }

      if (source.size() > longestSourcePhrase) {
        longestSourcePhrase = source.size();
      }
      if (target.size() > longestTargetPhrase) {
        longestTargetPhrase = target.size();
      }
    }
    reader.close();

    // print some status information
    long postPhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    System.err
        .printf(
            "Done loading phrase table: %s (mem used: %d MiB time: %.3f s)%n",
            f.getAbsolutePath(),
            (postPhraseTableLoadMemUsed - prePhraseTableLoadMemUsed)
                / (1024 * 1024), elapsedTime);
    System.err.println("Longest foreign phrase: " + longestSourcePhrase);
    return numScores;
  }

  @Override
  public int longestSourcePhrase() {
    return longestSourcePhrase;
  }
  
  @Override
  public int longestTargetPhrase() {
    return longestTargetPhrase;
  }

  @Override
  public List<Rule<IString>> query(
      Sequence<IString> sourceSequence) {
//    RawSequence<IString> rawForeign = new RawSequence<IString>(sourceSequence);
    
    int[] sourceArray = Sequences.toIntArray(sourceSequence);
//    int fIndex = sourceIndex.indexOf(sourceArray);
    int fIndex = sourceToRuleIndex.indexOf(sourceArray);
    if (fIndex == -1 || fIndex >= translations.size())
      return null;
    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
    if (intTransOpts == null)
      return null;
    List<Rule<IString>> transOpts = new ArrayList<Rule<IString>>(
        intTransOpts.size());
    for (IntArrayTranslationOption intTransOpt : intTransOpts) {
      RawSequence<IString> targetSequence = new RawSequence<IString>(
          intTransOpt.targetArray, IString.identityIndex());
      transOpts.add(new Rule<IString>(intTransOpt.id,
          intTransOpt.scores, scoreNames, targetSequence, sourceSequence,
          intTransOpt.alignment));
    }
    return transOpts;
  }
  
  @Override
  public int getId(Sequence<IString> sourceSequence,
      Sequence<IString> targetSequence) {
    int[] sourceArray = Sequences.toIntArray(sourceSequence);
    int fIndex = sourceToRuleIndex.indexOf(sourceArray);
    if (fIndex == -1 || fIndex >= translations.size()) {
      return -1;
    }
    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
    int[] targetArray = Sequences.toIntArray(targetSequence);
    for (IntArrayTranslationOption intTransOpt : intTransOpts) {
      if (Arrays.equals(targetArray, intTransOpt.targetArray)) {
        return intTransOpt.id;
      }
    }
    return -1;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return getName();
  }
  
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.out
          .println("Usage:\n\tjava ...FlatPhraseTable (phrasetable file) (entry to look up)");
      System.exit(-1);
    }

    String model = args[0];
    String phrase = args[1];
    long startTimeMillis = System.currentTimeMillis();
    System.out.printf("Loading phrase table: %s\n", model);
    FlatPhraseTable<String> ppt = new FlatPhraseTable<String>(null,
        model);
    long totalMemory = Runtime.getRuntime().totalMemory() / (1L << 20);
    long freeMemory = Runtime.getRuntime().freeMemory() / (1L << 20);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf(
        "size = %d, secs = %.3f, totalmem = %dm, freemem = %dm\n",
        ppt.size(), totalSecs, totalMemory, freeMemory);

    List<Rule<IString>> translationOptions = ppt
        .query(new SimpleSequence<IString>(IStrings
            .toIStringArray(phrase.split("\\s+"))));

    System.out.printf("Phrase: %s\n", phrase);

    if (translationOptions == null) {
      System.out.printf("No translation options found.");
      System.exit(-1);
    }

    System.out.printf("Options:\n");
    for (Rule<IString> opt : translationOptions) {
      System.out.printf("\t%s : %s\n", opt.target,
          Arrays.toString(opt.scores));
    }
  }

  @Override
  public int minRuleIndex() {
    return minRuleIndex;
  }
  
//  public static void createIndex(boolean withGaps) {
//    sourceIndex = (withGaps || TRIE_INDEX) ? new TrieIntegerArrayIndex()
//        : new ProbingIntegerArrayIndex();
//    ruleIndex = new ProbingIntegerArrayIndex();
//  }
//
//  public static void lockIndex() {
//    sourceIndex.lock();
//    ruleIndex.lock();
//  }
}
