package mt.base;

import edu.stanford.nlp.util.IString;

import java.util.*;


/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
public class TranslationOption<T> {
	public final float[] scores;
	public final String[] phraseScoreNames;
	public final RawSequence<T> translation;
	public final RawSequence<T> foreign;
	public final IString constilation;
	public final boolean forceAdd;
	private final int hashCode = super.hashCode();
	
	/**
	 * 
	 * @param scores
	 * @param translation
	 * @param foreign
	 */
	public TranslationOption(float[] scores, String[] phraseScoreNames, RawSequence<T> translation, RawSequence<T> foreign, IString constilation) {
		this.constilation = constilation;
		this.scores = Arrays.copyOf(scores, scores.length);
		this.translation = translation;
		this.foreign = foreign;
		this.phraseScoreNames = phraseScoreNames;
		this.forceAdd = false;
	}
	
	public TranslationOption(float[] scores, String[] phraseScoreNames, RawSequence<T> translation, RawSequence<T> foreign, IString constilation, boolean forceAdd) {
		this.constilation = constilation;
		this.scores = Arrays.copyOf(scores, scores.length);
		this.translation = translation;
		this.foreign = foreign;
		this.phraseScoreNames = phraseScoreNames;
		this.forceAdd = forceAdd;
	}
	
	@Override
	public String toString() {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append(String.format("TranslationOption: \"%s\" scores: %s\n", translation, Arrays.toString(scores)));
		return sbuf.toString();
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
}
