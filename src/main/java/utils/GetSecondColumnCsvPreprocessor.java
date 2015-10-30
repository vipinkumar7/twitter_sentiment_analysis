package utils;

import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;

/**
 * @author jeffreytang
 */
public class GetSecondColumnCsvPreprocessor implements SentencePreProcessor {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2624447543518814879L;

	public String preProcess(String s) {
		return s.split(",")[1].toLowerCase().replaceAll("[^a-zA-Z ]", "");
	}
}
