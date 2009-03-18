package mt.base;

import java.util.LinkedList;
import java.util.List;

import mt.decoder.feat.CombinedFeaturizer;
import mt.decoder.util.PhraseGenerator;
import mt.decoder.util.Scorer;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
abstract public class AbstractPhraseGenerator<TK,FV> implements PhraseGenerator<TK>, PhraseTable<TK> {
	private final CombinedFeaturizer<TK, FV> phraseFeaturizer;
	private final Scorer<FV> scorer;
	
	@SuppressWarnings("unchecked")
	public AbstractPhraseGenerator<TK,FV> clone() {
		try {
			return (AbstractPhraseGenerator<TK,FV>)super.clone();
		} catch (CloneNotSupportedException e ) { return null; /* will never happen */ } 
	}
	
	@Override
	public List<ConcreteTranslationOption<TK>> translationOptions(Sequence<TK> sequence, List<Sequence<TK>> targets, int translationId) {
		List<ConcreteTranslationOption<TK>> opts = new LinkedList<ConcreteTranslationOption<TK>>();
		int sequenceSz = sequence.size();
		int longestForeignPhrase = this.longestForeignPhrase();
		for (int startIdx = 0; startIdx < sequenceSz; startIdx++) {
			for (int len = 1; len <= longestForeignPhrase; len++) {
				int endIdx = startIdx+len;					
				if (endIdx > sequenceSz) break;
				CoverageSet foreignCoverage = new CoverageSet(sequenceSz);
				foreignCoverage.set(startIdx, endIdx);
				Sequence<TK>foreignPhrase = sequence.subsequence(startIdx, endIdx);
				List<TranslationOption<TK>> abstractOpts = this.getTranslationOptions(foreignPhrase);
				if (abstractOpts == null) continue;
				for (TranslationOption<TK> abstractOpt : abstractOpts) {
					opts.add(new ConcreteTranslationOption<TK>(abstractOpt, foreignCoverage, phraseFeaturizer, scorer, sequence, this.getName(), translationId));
				}
			}
		}
		return opts;
	}
	
	public AbstractPhraseGenerator(CombinedFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
		this.phraseFeaturizer = phraseFeaturizer;
		this.scorer = scorer;
	}

	@Override
	abstract public String getName();

	@Override
	abstract public List<TranslationOption<TK>> getTranslationOptions(Sequence<TK> sequence);

	@Override
	abstract public int longestForeignPhrase();

}
