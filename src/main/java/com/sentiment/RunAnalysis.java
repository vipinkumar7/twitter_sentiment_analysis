package com.sentiment;
/**
 * @author jeffreytang
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.util.Pair;

import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.InMemoryLookupCache;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.FileSentenceIterator;
import utils.GetSecondColumnCsvPreprocessor;
import utils.ParseCsvPreprocessor;

public class RunAnalysis {
	private static Logger log = LoggerFactory.getLogger(RunAnalysis.class);
	private String word2vecTxtFilePath;
	private String trainFileName;
	private String testFileName;
	private File wordVectorFile;
	private String averageTweetVectorsFileName;
	private int vectorLength;

	public RunAnalysis(String trainFileName, String testFileName,
			int vectorLength) {
		this.trainFileName = trainFileName;
		this.testFileName = testFileName;
		String fileName = trainFileName.split("\\.")[0];
		this.word2vecTxtFilePath = fileName + "_word_vectors.txt";
		this.wordVectorFile = new File(word2vecTxtFilePath);
		this.averageTweetVectorsFileName = fileName + "_tweet_vectors.csv";
		this.vectorLength = vectorLength;
	}

	public void runWord2Vec() throws Exception {
		if (!wordVectorFile.isFile()) {

			log.info("Parse CSV file from file...");
			GetSecondColumnCsvPreprocessor csvSentencePreprocessor = new GetSecondColumnCsvPreprocessor();
			FileSentenceIterator it = new FileSentenceIterator(
					csvSentencePreprocessor, trainFileName);
			InMemoryLookupCache cache = new InMemoryLookupCache();
			WeightLookupTable table = new InMemoryLookupTable.Builder()
					.vectorLength(vectorLength).useAdaGrad(false).cache(cache)
					.lr(0.025f).build();

			log.info("Building model....");
			Word2Vec vec = new Word2Vec.Builder().minWordFrequency(5)
					.iterations(3).layerSize(vectorLength).lookupTable(table)
					.vocabCache(cache).seed(42).windowSize(5).iterate(it)
					.build();

			log.info("Training model...");
			vec.fit();

			log.info("Writing word vectors to file...");
			WordVectorSerializer.writeWordVectors(vec, word2vecTxtFilePath);
		}
	}

	public Pair<ArrayList<INDArray>, ArrayList<INDArray>> computeAvgWordVector()
			throws Exception {
		if (wordVectorFile.isFile()) {

			ArrayList<INDArray> labelVectorList = new ArrayList<>();
			ArrayList<INDArray> avgTweetVectorList = new ArrayList<>();

			// Load word vectors from runWord2Vec()
			WordVectors wordVectors = WordVectorSerializer
					.loadTxtVectors(wordVectorFile);

			// Parse the csv file again to get label and average word vector
			ParseCsvPreprocessor parseCsvPreprocessor = new ParseCsvPreprocessor();
			FileSentenceIterator it = new FileSentenceIterator(
					parseCsvPreprocessor, trainFileName);
			while (it.hasNext()) {
				// Add label to list
				Pair<double[], String[]> labelAndTweet = it
						.nextSentenceParsedCsv();
				// First column is if it is a pos sentiment, second column is
				// whether it's neg
				INDArray labelVector = Nd4j.create(labelAndTweet.getKey());
				labelVectorList.add(labelVector);

				// Add avg tweet vector to list
				String[] words = labelAndTweet.getValue();
				INDArray sumTweetVector = Nd4j.zeros(1, vectorLength);
				for (String word : words) {
					sumTweetVector.addi(wordVectors.getWordVectorMatrix(word));
				}
				INDArray averageTweetVector = sumTweetVector.div(words.length);
				avgTweetVectorList.add(averageTweetVector);
			}

			return new Pair<>(avgTweetVectorList, labelVectorList);
		} else {
			throw new IllegalStateException(
					"Run runWord2Vec() first to get the word vectors.");
		}
	}

	public MultiLayerNetwork trainSentimentClassifier(
			Pair<ArrayList<INDArray>, ArrayList<INDArray>> pair)
			throws Exception {

		List<INDArray> featureList = pair.getKey();
		List<INDArray> targetList = pair.getValue();

		log.info("Build model....");
		MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
				.seed(42)
				.iterations(1)
				.learningRate(1e-3)
				.l1(0.3)
				.regularization(true)
				.l2(1e-3)
				.list(1)
				.layer(0,
						new OutputLayer.Builder().activation("softmax")
								.nIn(vectorLength).nOut(2).build()).build();

		MultiLayerNetwork model = new MultiLayerNetwork(conf);
		model.init();
		for (int i = 0; i < featureList.size(); i++) {
			INDArray featureRow = featureList.get(i);
			INDArray labelRow = targetList.get(i);
			model.fit(featureRow, labelRow);
		}

		Evaluation eval = new Evaluation();
		for (int i = 0; i < featureList.size(); i++) {
			INDArray featureRow = featureList.get(i);
			INDArray labelRow = targetList.get(i);
			INDArray output = model.output(featureRow);
			eval.eval(labelRow, output);
		}
		log.info(eval.stats());

		return model;
	}

	public static void main(String args[]) throws Exception {
		String trainDataFileName = "/home/vipin/SentimentDataset.csv";
		String testDataFileName = "sentiment140_test.csv";
		int vectorLength = 200;

		RunAnalysis runAnalysis = new RunAnalysis(trainDataFileName,
				testDataFileName, vectorLength);
		runAnalysis.runWord2Vec();
		Pair<ArrayList<INDArray>, ArrayList<INDArray>> targetFeaturePair = runAnalysis
				.computeAvgWordVector();
		MultiLayerNetwork model = runAnalysis
				.trainSentimentClassifier(targetFeaturePair);
	}
}
