package learning;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import semiring.Derivation;
import tagging_hypergraph.TaggingHypergraphGenerator;
import decoding.Decoder;
import decoding.Viterbi3;

import hypergraph.HypergraphProto.Hypergraph;

/**
 * Learn new parameters for sequence labeling using a Perceptron.
 * Data: gene.train
 * @author swabha
 *
 */

public class Perceptron {
	private Viterbi3 viterbi = new Viterbi3(1);
	
	private int numIterations;
	
	Perceptron(int numIterations) {
		this.numIterations = numIterations;
	}
	
	Map<String, Double> run(List<LearningExample> examples, Map<String, Double> weightsMap) {
		for (int i = 0; i < numIterations; i++) {
			for (LearningExample example : examples) {
				
				List<String> tokens = example.getTokens();
				List<String> trueTags = example.getTagSequence();
				Map<String, Double> trueFeatures = 
						PerceptronUtils.getFeatureVector(tokens, trueTags);
				Hypergraph sentenceHypergraph = TaggingHypergraphGenerator.buildTaggingHypergraph(
						tokens, weightsMap);
				int rootVertex = sentenceHypergraph.getVerticesCount() - 1;
				
				// Get the predicted tag sequence
				Map<Integer, List<Derivation>> derivations = viterbi.run(sentenceHypergraph);
				System.out.println(derivations.size());
				Decoder decoder = new Decoder();
				String backPointers = decoder.getKBestPaths(
						derivations.get(rootVertex), sentenceHypergraph);
				List<String> predictedTags = Arrays.asList(backPointers.split(" "));
				
				// TODO: fix this!
				if (backPointers.equals(example.getTagSequence())) {
					// do nothing
				} else {
					// update weightsMap
					Map<String, Double> predictedFeatures = 
							PerceptronUtils.getFeatureVector(tokens, predictedTags);
					Map<String, Double> difference = 
							PerceptronUtils.mapSubtraction(trueFeatures, predictedFeatures);
					Map<String, Double> weighMap = 
							PerceptronUtils.mapAddition(weightsMap, difference);
					// construct new hypergraph using the new weightsMap
				}
			}
		}
		return weightsMap;
	}
	
	public static void main(String[] args) {
		Perceptron perceptron = new Perceptron(5);
		Map<String, Double> initialWeights = InputReader.readWeights(new File("data/tag.model"));
		List<LearningExample> examples = InputReader.readExample(
				new File("data/gene.train"), initialWeights);
		Map<String, Double> finalWeights = perceptron.run(examples, initialWeights);
	}

}
