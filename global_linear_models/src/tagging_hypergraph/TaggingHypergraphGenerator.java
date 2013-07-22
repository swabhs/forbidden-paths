package tagging_hypergraph;

import hypergraph.HypergraphProto.Hyperedge;
import hypergraph.HypergraphProto.Hypergraph;
import hypergraph.HypergraphProto.Vertex;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;

import learning.InputReader;

/**
 * Generates a hypergraph for the tagging task. We use global linear models here, each history
 * containing information about the trigram. 
 * Each node in the hypergraph is a bigram - the previous and current labels. Each hyperarc is a
 * normal arc.
 * @author swabha
 * TODO(swabha): Test to see if the weights are as expected.
 * TODO(swabha): Add the suffix features
 */

public class TaggingHypergraphGenerator {
	
	static final List<String> tags = Arrays.asList("I-GENE", "O");
	/**
	 * Builds a hypergraph for a list of tokens and a list of tags
	 * @param tokens a token should not contain a '_'
	 * @param tags
	 * @return
	 */
	public static Hypergraph buildTaggingHypergraph (
			List<String> tokens, Map<String, Double> weights) {
		final int numTags = tags.size();
		// Vertex Maps
		Map<Integer, List<Integer>> outEdgeMap = new TreeMap<Integer, List<Integer>>();
		Map<Integer, List<Integer>> inEdgeMap = new TreeMap<Integer, List<Integer>>();
		Map<Integer, String> vertexNameMap = new TreeMap<Integer, String>();
		
		// Edge Maps
		Map<Integer, List<Integer>> childMap = new TreeMap<Integer, List<Integer>>();
		Map<Integer, Integer> parentMap = new TreeMap<Integer, Integer>();
		
		int vertexId = 0; int edgeId = 0;
		
		// add the first numTags vertices
		for (String tag : tags) {
			vertexNameMap.put(vertexId, tokens.get(0) + "_*_" + tag);
			
			outEdgeMap.put(vertexId, HypGeneratorUtils.getConsecutiveIntegers(edgeId, numTags * numTags));
			int firstNextLevelVertex = numTags * numTags  + numTags;
			for (int i = 0; i < numTags * numTags; i++) {
				int parent = firstNextLevelVertex + i;
				parentMap.put(edgeId, parent);
				childMap.put(edgeId, Arrays.asList(vertexId));
				
				List<Integer> incoming;
				if (inEdgeMap.containsKey(parent)){
					incoming = inEdgeMap.get(parent);
				} else {
					incoming = new ArrayList<Integer>();
				}
				incoming.add(edgeId);
				inEdgeMap.put(parent, incoming);
				
				++edgeId;
			}
			
			System.out.println(vertexId + " " + vertexNameMap.get(vertexId));
			System.out.println("outedges: " + outEdgeMap.get(vertexId));
			System.out.println("inedges: " + inEdgeMap.get(vertexId));
			++vertexId;
		} 
		
		for (String token : tokens) {
			int firstNextLevelVertex = numTags * numTags * (tokens.indexOf(token) + 1) + numTags;
			for (String firstTag : tags) {
				for (String secondTag : tags) {
					vertexNameMap.put(vertexId, token + "_" + firstTag + "_" + secondTag);
					
					// if not last position, add numTags^2 outgoing edges
					if (tokens.indexOf(token) != tokens.size() - 1) {
						outEdgeMap.put(vertexId, 
								HypGeneratorUtils.getConsecutiveIntegers(edgeId, numTags*numTags));
						// add edges
						for (int i = 0; i < numTags * numTags; i++) {
							childMap.put(edgeId, Arrays.asList(vertexId));
							int parent = firstNextLevelVertex + i;
							parentMap.put(edgeId, parent);
							
							// update incoming of parent
							List<Integer> incoming;
							if (inEdgeMap.containsKey(parent)){
								incoming = inEdgeMap.get(parent);
							} else {
								incoming = new ArrayList<Integer>();
							}
							incoming.add(edgeId);
							inEdgeMap.put(parent, incoming);
							
							++edgeId;
						}
					} else { // if last token
						childMap.put(edgeId, Arrays.asList(vertexId));
						int parent = firstNextLevelVertex;
						parentMap.put(edgeId, parent);
						
						// update incoming of parent
						List<Integer> incoming;
						if (inEdgeMap.containsKey(parent)){
							incoming = inEdgeMap.get(parent);
						} else {
							incoming = new ArrayList<Integer>();
						}
						incoming.add(edgeId);
						inEdgeMap.put(parent, incoming);
						
						outEdgeMap.put(vertexId, Arrays.asList(edgeId));
						
						++edgeId;
					}
					System.out.println(vertexId + " " + vertexNameMap.get(vertexId));
					System.out.println("outedges: " + outEdgeMap.get(vertexId));
					System.out.println("inedges: " + inEdgeMap.get(vertexId));
					++vertexId;
				}
			}
		}
		// add STOP vertex
		vertexNameMap.put(vertexId, "STOP");
		System.out.println(vertexId + " " + vertexNameMap.get(vertexId));
		System.out.println("outedges: " + outEdgeMap.get(vertexId));
		System.out.println("inedges: " + inEdgeMap.get(vertexId));
		System.out.println("#edges: " + edgeId); System.out.println(childMap.size());
		
		Map<Integer, Double> edgeWeightMap = addWeightsToHypergraph(
				childMap, parentMap, vertexNameMap, tags, weights);
		
		List<Hyperedge> edges = new ArrayList<Hyperedge>();
		for (Integer eId : parentMap.keySet()) {
			edges.add(Hyperedge.newBuilder()
					.setId(eId)
					.setWeight(edgeWeightMap.get(eId))
					.addAllChildrenIds(childMap.get(eId))
					.setParentId(parentMap.get(eId))
					.build());
		}
		
		List<Vertex> vertices = new ArrayList<Vertex>();
		for (Integer vId : inEdgeMap.keySet()) {
			List<Integer> outEdges = outEdgeMap.get(vId);
			if (outEdges == null) {
				outEdges = new ArrayList<Integer>();
			}
			vertices.add(Vertex.newBuilder()
					.setId(vId)
					.setName(vertexNameMap.get(vId))
					.addAllInEdge(inEdgeMap.get(vId))
					.addAllOutEdge(outEdges)
					.build());
		}
		
		Hypergraph.Builder builder = Hypergraph.newBuilder();
		return builder.addAllEdges(edges).addAllVertices(vertices).build();
		//return null;
	}

	/**
	 * Three kinds of edges: 
	 * between level 1 and 2, 
	 * between last and STOP level
	 * between all other levels
	 * @param childMap
	 * @param parentMap
	 * @return
	 */
	static Map<Integer, Double> addWeightsToHypergraph(
			Map<Integer, List<Integer>> childMap, 
			Map<Integer, Integer> parentMap,
			Map<Integer, String> vertexNameMap,
			List<String> tags,
			Map<String, Double> weights) {
		
		int numTags = tags.size();
		
		Map<Integer, Double> edgeWeightMap = new TreeMap<Integer, Double>();
		for (int edge : childMap.keySet()) {
			System.out.println("edge id: " + edge);
			
			String childVertexName = vertexNameMap.get(childMap.get(edge).get(0));
			String[] childTags = childVertexName.split("_");
			System.out.println("child: " + childVertexName);
			
			String parentVertexName = vertexNameMap.get(parentMap.get(edge));
			String[] parentTags = parentVertexName.split("_");
			System.out.println("parent: " + parentVertexName);
			
			System.out.println("___________________________-");
			if (!parentVertexName.equals("STOP") && parentTags[1].equals(childTags[2])) {
				edgeWeightMap.put(edge, 0.0);
				continue;
			}
			
			
			double weight = 0.0;
			String tag = "TAG:" + childTags[0] + ":" + childTags[2];
			if (weights.containsKey(tag)) {
				weight += weights.get("TAG:" + childTags[0] + ":" + childTags[2]);
			}
			if (edge < numTags * numTags) { // edge type 1
				String firstTrigram = "TRIGRAM:*:*:" + parentTags[1];
				if (weights.containsKey(firstTrigram)) {
					weight += weights.get(firstTrigram); 
				}
				
				String secondTrigram = "TRIGRAM:*:" + parentTags[1] + ":" + parentTags[2];
				if (weights.containsKey(secondTrigram))
					weight += weights.get(secondTrigram);
			
			} else if (edge >= childMap.size() - numTags * numTags) { // edge type 3
				String trigram = "TRIGRAM:" + childTags[1] + ":" + childTags[2] + ":STOP";
				if (weights.containsKey(trigram)) {
					weight += weights.get(trigram); 
				}
			// Edge type 2
			} else {
				String trigram = "TRIGRAM:" + childTags[1] + ":" + childTags[2] + ":" + parentTags[2];
				if (weights.containsKey(trigram)) {
					weight += weights.get(trigram);
				}
			}
			
			edgeWeightMap.put(edge, weight);
		}
		for (int edge : edgeWeightMap.keySet()) {
			System.out.println(edge + " " + edgeWeightMap.get(edge));
		}
		return edgeWeightMap;
	}
	
	public static void main(String[] args) {
		List<String> sentence = Arrays.asList(
				"GCR1", "gene", "function", "is", "required", "for", "high", "-", "level",
				"glycolytic", "gene", "expression", "in", "Saccharomyces", "cerevisiae", ".");
		
		Map<String, Double> weightsMap = InputReader.readWeights(new File("data/tag.model"));
		Hypergraph h = TaggingHypergraphGenerator.buildTaggingHypergraph(sentence, weightsMap);
		try {
			FileOutputStream mOutput = new FileOutputStream("hyp_example", true);
			h.writeTo(mOutput);
			mOutput.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
