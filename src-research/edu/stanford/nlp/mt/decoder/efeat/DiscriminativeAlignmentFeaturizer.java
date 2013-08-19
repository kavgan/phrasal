package edu.stanford.nlp.mt.decoder.efeat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.decoder.feat.NeedsInternalAlignments;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;

/**
 * Indicator features for aligned and unaligned tokens in phrase pairs.
 * 
 * @author Spence Green
 *
 */
public class DiscriminativeAlignmentFeaturizer implements NeedsInternalAlignments,
RuleFeaturizer<IString,String> {

  private static final String FEATURE_NAME = "Align";
  private static final String FEATURE_NAME_TGT = "UnAlignTgt";
  private static final String FEATURE_NAME_SRC = "UnAlignSrc";

  private static final double DEFAULT_FEATURE_VALUE = 1.0;
  
  private final boolean addSourceDeletions;
  private final boolean addTargetInsertions;
  private final double featureValue;
  
  public DiscriminativeAlignmentFeaturizer() { 
    addSourceDeletions = false;
    addTargetInsertions = false;
    featureValue = DEFAULT_FEATURE_VALUE;
  }

  public DiscriminativeAlignmentFeaturizer(String...args) {
    addSourceDeletions = args.length > 0 ? Boolean.parseBoolean(args[0]) : false;
    addTargetInsertions = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
    featureValue = args.length > 2 ? Double.parseDouble(args[2]) : DEFAULT_FEATURE_VALUE;
  }

  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    final int eLength = f.targetPhrase.size();
    final int fLength = f.sourcePhrase.size();
    List<Set<String>> f2e = new ArrayList<Set<String>>(fLength);
    for (int i = 0; i < fLength; ++i) {
      f2e.add(new HashSet<String>());
    }
    boolean[] fIsAligned = new boolean[fLength];
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();

    // Iterate over target side of phrase
    for (int i = 0; i < eLength; ++i) {
      int[] fIndices = alignment.t2s(i);
      String eWord = f.targetPhrase.get(i).toString();
      
      if (fIndices == null) {
        // Unaligned target word
        if (addTargetInsertions) {
          String feature = makeFeatureString(FEATURE_NAME_TGT, eWord);
          features.add(new FeatureValue<String>(feature, featureValue));
        }

      } else {
        // This is hairy. We want to account for many-to-one alignments efficiently.
        // Therefore, copy all foreign tokens into the bucket for the first aligned
        // foreign index. Then we can iterate once over the source.
        int fInsertionIndex = -1;
        for (int fIndex : fIndices) {
          if (fInsertionIndex < 0) {
            fInsertionIndex = fIndex;
            f2e.get(fInsertionIndex).add(eWord);
          } else {
            String fWord = f.sourcePhrase.get(fIndex).toString();
            f2e.get(fInsertionIndex).add(fWord);
          }
          fIsAligned[fIndex] = true;
        }
      }
    }

    // Iterate over source side of phrase
    for (int i = 0; i < fLength; ++i) {
      Set<String> eWords = f2e.get(i);
      String fWord = f.sourcePhrase.get(i).toString();
      if ( ! fIsAligned[i]) {
        if (addSourceDeletions) {
          String feature = makeFeatureString(FEATURE_NAME_SRC, fWord);
          features.add(new FeatureValue<String>(feature, featureValue));
        }
      } else if (eWords.size() > 0){
        List<String> alignedWords = new ArrayList<String>(eWords.size() + 1);
        alignedWords.add(fWord);
        alignedWords.addAll(eWords);
        Collections.sort(alignedWords);
        StringBuilder sb = new StringBuilder();
        int len = alignedWords.size();
        for (int j = 0; j < len; ++j) {
          if (j != 0) sb.append("-");
          sb.append(alignedWords.get(j));
        }
        String feature = makeFeatureString(FEATURE_NAME, sb.toString());
        features.add(new FeatureValue<String>(feature, featureValue));
      }
    }
    return features;
  }

  private String makeFeatureString(String featureName, String featureSuffix) {
    return String.format("%s:%s", featureName, featureSuffix);
  }
}
