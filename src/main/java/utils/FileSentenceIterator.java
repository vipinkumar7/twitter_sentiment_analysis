package utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javafx.util.Pair;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.deeplearning4j.text.sentenceiterator.BaseSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Vipin Kumar
 *
 */
public class FileSentenceIterator extends BaseSentenceIterator {
	private static Logger log = LoggerFactory
			.getLogger(S3SentenceIterator.class);

	private String fileToRead;
	private LineIterator iter;
	private ParseCsvPreprocessor parseCsvPreprocessor;

	public FileSentenceIterator(String fileToRead) {
		this.fileToRead = fileToRead;
		this.preProcessor = null;
		readFile();
	}

	public FileSentenceIterator(SentencePreProcessor preProcessor,
			String fileToRead) {
		super(preProcessor);
		this.fileToRead = fileToRead;
		this.preProcessor = preProcessor;
		readFile();
	}

	public FileSentenceIterator(ParseCsvPreprocessor parseCsvPreprocessor,
			String fileToRead) {
		this.fileToRead = fileToRead;
		this.parseCsvPreprocessor = parseCsvPreprocessor;
		readFile();
	}

	public void readFile() {
		try {
			InputStream in = new FileInputStream(new File(fileToRead));
			BufferedInputStream file = new BufferedInputStream(in);
			this.iter = IOUtils.lineIterator(file, "UTF-8");
		} catch (IOException e) {
			log.info("File not read properly");
		}

	}

	public Pair<double[], String[]> nextSentenceParsedCsv() {
		String line = this.iter.nextLine();
		if (this.parseCsvPreprocessor != null) {
			return this.parseCsvPreprocessor.preProcess(line);
		} else {
			throw new IllegalStateException(
					"utils.ParseCsvPreprocessor not defined.");
		}
	}

	public String nextSentence() {
		String line = this.iter.nextLine();
		if (this.preProcessor != null) {
			line = this.preProcessor.preProcess(line);
		}
		return line;
	}

	public boolean hasNext() {
		return this.iter.hasNext();
	}

	public void reset() {
		readFile();
	}
}
