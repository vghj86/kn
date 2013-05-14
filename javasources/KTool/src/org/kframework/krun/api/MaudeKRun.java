package org.kframework.krun.api;

import org.apache.commons.collections15.Transformer;
import org.kframework.backend.maude.MaudeFilter;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.*;
import org.kframework.kil.loader.DefinitionHelper;
import org.kframework.kil.visitors.exceptions.TransformerException;
import org.kframework.krun.runner.KRunner;
import org.kframework.krun.*;
import org.kframework.krun.Error;
import org.kframework.krun.api.Transition.TransitionType;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.general.GlobalSettings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.uci.ics.jung.graph.*;
import edu.uci.ics.jung.io.graphml.*;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class MaudeKRun implements KRun {
	protected DefinitionHelper definitionHelper;
	public MaudeKRun(DefinitionHelper definitionHelper) {
		this.definitionHelper = definitionHelper;
	}
	
	private void executeKRun(String maudeCmd, boolean ioServer) throws KRunExecutionException {
		FileUtil.createFile(K.maude_in, maudeCmd);
		File outFile = FileUtil.createFile(K.maude_out);
		File errFile = FileUtil.createFile(K.maude_err);

		int returnValue;
		try {
			if (K.log_io) {
				returnValue = KRunner.main(new String[] { "--maudeFile", K.compiled_def + K.fileSeparator + "main.maude", "--moduleName", K.main_module, "--commandFile", K.maude_in, "--outputFile", outFile.getCanonicalPath(), "--errorFile", errFile.getCanonicalPath(), "--createLogs" }, definitionHelper);
			}
			if (!ioServer) {
				returnValue = KRunner.main(new String[] { "--maudeFile", K.compiled_def + K.fileSeparator + "main.maude", "--moduleName", K.main_module, "--commandFile", K.maude_in, "--outputFile", outFile.getCanonicalPath(), "--errorFile", errFile.getCanonicalPath(), "--noServer" }, definitionHelper);
			} else {
				returnValue = KRunner.main(new String[] { "--maudeFile", K.compiled_def + K.fileSeparator + "main.maude", "--moduleName", K.main_module, "--commandFile", K.maude_in, "--outputFile", outFile.getCanonicalPath(), "--errorFile", errFile.getCanonicalPath() }, definitionHelper);
			}
		} catch (Exception e) {
			throw new RuntimeException("Runner threw exception", e);
		}
		if (errFile.exists()) {
			String content = FileUtil.getFileContent(K.maude_err);
			if (content.length() > 0) {
				throw new KRunExecutionException(content);
			}
		}
		if (returnValue != 0) {
			Error.report("Maude returned non-zero value: " + returnValue);
		}

	}

	public KRunResult<KRunState> run(Term cfg) throws KRunExecutionException {
		MaudeFilter maudeFilter = new MaudeFilter(definitionHelper);
		cfg.accept(maudeFilter);
		String cmd = "set show command off ." + K.lineSeparator + setCounter() + K.maude_cmd + " " + maudeFilter.getResult() + " .";
		if(K.trace) {
			cmd = "set trace on ." + K.lineSeparator + cmd;
		}
		if(K.profile) {
			cmd = "set profile on ." + K.lineSeparator + cmd + K.lineSeparator + "show profile .";
		}
		cmd += getCounter();

		executeKRun(cmd, K.io);
		try {
			return parseRunResult();
		} catch (IOException e) {
			throw new RuntimeException("Pretty-printer threw I/O exception", e);
		}
	}

	private String setCounter() {
		return "red setCounter(" + Integer.toString(K.counter) + ") ." + K.lineSeparator;
	}

	private String getCounter() {
		return K.lineSeparator + "red counter .";
	}

	public KRunResult<KRunState> step(Term cfg, int steps) throws KRunExecutionException {
		String maude_cmd = K.maude_cmd;
		if (steps == 0) {
			K.maude_cmd = "red";
		} else {
			K.maude_cmd = "rew[" + Integer.toString(steps) + "]";
		}
		KRunResult<KRunState> result = run(cfg);
		K.maude_cmd = maude_cmd;
		return result;
	}

	//needed for --statistics command
	private String printStatistics(Element elem) {
		String result = "";
		if ("search".equals(K.maude_cmd)) {
			String totalStates = elem.getAttribute("total-states");
			String totalRewrites = elem.getAttribute("total-rewrites");
			String realTime = elem.getAttribute("real-time-ms");
			String cpuTime = elem.getAttribute("cpu-time-ms");
			String rewritesPerSecond = elem.getAttribute("rewrites-per-second");
			result += "states: " + totalStates + " rewrites: " + totalRewrites + " in " + cpuTime + "ms cpu (" + realTime + "ms real) (" + rewritesPerSecond + " rewrites/second)";
		} else if ("erewrite".equals(K.maude_cmd)){
			String totalRewrites = elem.getAttribute("total-rewrites");
			String realTime = elem.getAttribute("real-time-ms");
			String cpuTime = elem.getAttribute("cpu-time-ms");
			String rewritesPerSecond = elem.getAttribute("rewrites-per-second");
			result += "rewrites: " + totalRewrites + " in " + cpuTime + "ms cpu (" + realTime + "ms real) (" + rewritesPerSecond + " rewrites/second)";
		}
		return result;
	}



	private KRunResult<KRunState> parseRunResult() throws IOException {
		File input = new File(K.maude_output);
		Document doc = XmlUtil.readXML(input);
		NodeList list = null;
		Node nod = null;
		list = doc.getElementsByTagName("result");
		nod = list.item(1);

		assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
		Element elem = (Element) nod;
		List<Element> child = XmlUtil.getChildElements(elem);
		assertXML(child.size() == 1);

		KRunState state = parseElement((Element) child.get(0), definitionHelper);
		KRunResult<KRunState> ret = new KRunResult<KRunState>(state);
		String statistics = printStatistics(elem);
		ret.setStatistics(statistics);
		ret.setRawOutput(FileUtil.getFileContent(K.maude_out));
		parseCounter(list.item(2));
		return ret;
	}

	private KRunState parseElement(Element el, DefinitionHelper definitionHelper) {
		Term rawResult = MaudeKRun.parseXML(el, definitionHelper);

		return new KRunState(rawResult, definitionHelper);
	}

	private void parseCounter(Node counter) {
		assertXML(counter != null && counter.getNodeType() == Node.ELEMENT_NODE);
		Element elem = (Element) counter;
		List<Element> child = XmlUtil.getChildElements(elem);
		assertXML(child.size() == 1);
		IntBuiltin intBuiltin = (IntBuiltin) parseXML(child.get(0), definitionHelper);
		K.counter = intBuiltin.bigIntegerValue().intValue() - 1;
	}

	private static void assertXML(boolean assertion) {
		if (!assertion) {
			GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "Cannot parse result xml from maude. If you believe this to be in error, please file a bug and attach " + K.maude_output.replaceAll("/krun[0-9]*/", "/krun/")));
		}
	}

	private static void assertXMLTerm(boolean assertion) throws Exception {
		if (!assertion) {
			throw new Exception();
		}
	}

	public static Term parseXML(Element xml, DefinitionHelper definitionHelper) {
		String op = xml.getAttribute("op");
		String sort = xml.getAttribute("sort");
		sort = sort.replaceAll("`([{}\\[\\](),])", "$1");
		List<Element> list = XmlUtil.getChildElements(xml);
		
		try {
			Pattern pattern = Pattern.compile("<([^_>]+)>(_+)</([^_>]+)>");
			Matcher m = pattern.matcher(op);
			if ((sort.equals("BagItem") || sort.equals("[Bag]")) && op.equals("<_>_</_>")) {
				Cell cell = new Cell();
				assertXMLTerm(list.size() == 3 && list.get(0).getAttribute("sort").equals("CellLabel") && list.get(2).getAttribute("sort").equals("CellLabel") && list.get(0).getAttribute("op").equals(list.get(2).getAttribute("op")));

				cell.setLabel(list.get(0).getAttribute("op"));
				cell.setContents(parseXML(list.get(1), definitionHelper));
				return cell;
			} else if (sort.equals("BagItem") && m.matches()) {
				Cell cell = new Cell();
				assertXMLTerm(list.size() == m.group(2).length() && m.group(1).equals(m.group(3)));
				cell.setLabel(m.group(1));
				if (m.group(2).length() > 1) {
					Bag bag = new Bag();
					for (Element el : list) {
						bag.getContents().add(parseXML(el, definitionHelper));
					}
					cell.setContents(bag);
				} else {
					cell.setContents(parseXML(list.get(0), definitionHelper));
				}
				return cell;
			} else if ((sort.equals("BagItem") || sort.equals("[Bag]")) && op.equals("BagItem")) {
				assertXMLTerm(list.size() == 1);
				return new BagItem(parseXML(list.get(0), definitionHelper));
			} else if ((sort.equals("MapItem") || sort.equals("[Map]")) && op.equals("_|->_")) {
				assertXMLTerm(list.size() == 2);
				return new MapItem(parseXML(list.get(0), definitionHelper), parseXML(list.get(1), definitionHelper));
			} else if ((sort.equals("SetItem") || sort.equals("[Set]")) && op.equals("SetItem")) {
				assertXMLTerm(list.size() == 1);
				return new SetItem(parseXML(list.get(0), definitionHelper));
			} else if ((sort.equals("ListItem") || sort.equals("[List]")) && op.equals("ListItem")) {
				assertXMLTerm(list.size() == 1);
				return new ListItem(parseXML(list.get(0), definitionHelper));
			} else if (op.equals("_`,`,_") && sort.equals("NeKList")) {
				assertXMLTerm(list.size() >= 2);
				List<Term> l = new ArrayList<Term>();
				for (Element elem : list) {
					l.add(parseXML(elem, definitionHelper));
				}
				return new KList(l);
			} else if (sort.equals("K") && op.equals("_~>_")) {
				assertXMLTerm(list.size() >= 2);
				List<Term> l = new ArrayList<Term>();
				for (Element elem : list) {
					l.add(parseXML(elem, definitionHelper));
				}
				return new KSequence(l);
			} else if (op.equals("__") && (sort.equals("NeList") || sort.equals("List") || sort.equals("[List]"))) {
				assertXMLTerm(list.size() >= 2);
				List<Term> l = new ArrayList<Term>();
				for (Element elem : list) {
					l.add(parseXML(elem, definitionHelper));
				}
				return new org.kframework.kil.List(l);
			} else if (op.equals("__") && (sort.equals("NeBag") || sort.equals("Bag") || sort.equals("[Bag]"))) {
				assertXMLTerm(list.size() >= 2);
				List<Term> l = new ArrayList<Term>();
				for (Element elem : list) {
					l.add(parseXML(elem, definitionHelper));
				}
				return new Bag(l);
			} else if (op.equals("__") && (sort.equals("NeSet") || sort.equals("Set") || sort.equals("[Set]"))) {
				assertXMLTerm(list.size() >= 2);
				List<Term> l = new ArrayList<Term>();
				for (Element elem : list) {
					l.add(parseXML(elem, definitionHelper));
				}
				return new org.kframework.kil.Set(l);
			} else if (op.equals("__") && (sort.equals("NeMap") || sort.equals("Map") || sort.equals("[Map]"))) {
				assertXMLTerm(list.size() >= 2);
				List<Term> l = new ArrayList<Term>();
				for (Element elem : list) {
					l.add(parseXML(elem, definitionHelper));
				}
				return new org.kframework.kil.Map(l);
			} else if ((op.equals("#_") || op.equals("List2KLabel_") || op.equals("Map2KLabel_") || op.equals("Set2KLabel_") || op.equals("Bag2KLabel_") || op.equals("KList2KLabel_") || op.equals("KLabel2KLabel_")) && (sort.equals(KSorts.KLABEL) || sort.equals("[KLabel]"))) {
				assertXMLTerm(list.size() == 1);
				return new KInjectedLabel(parseXML(list.get(0), definitionHelper));
			} else if (sort.equals("#NzInt") && op.equals("--Int_")) {
				assertXMLTerm(list.size() == 1);
				return IntBuiltin.of("-" + ((IntBuiltin) parseXML(list.get(0), definitionHelper)).getValue());
			} else if (sort.equals("#NzNat") && op.equals("sNat_")) {
				assertXMLTerm(list.size() == 1 && parseXML(list.get(0), definitionHelper).equals(IntBuiltin.ZERO));
                return IntBuiltin.of(xml.getAttribute("number"));
			} else if (sort.equals("#Zero") && op.equals("0")) {
				assertXMLTerm(list.size() == 0);
				return IntBuiltin.ZERO;
			} else if (sort.equals("#Bool") && (op.equals("true") || op.equals("false"))) {
				assertXMLTerm(list.size() == 0);
                return BoolBuiltin.of(op);
			} else if (sort.equals("#Char") || sort.equals("#String")) {
				assertXMLTerm(list.size() == 0);
                assertXMLTerm(op.startsWith("\"") && op.endsWith("\""));
				return StringBuiltin.of(op.substring(1, op.length() - 1));
			} else if (sort.equals("#FiniteFloat")) {
				assertXMLTerm(list.size() == 0);
				return FloatBuiltin.of(op);
			} else if (sort.equals("#Id") && op.equals("#id_")) {
				assertXMLTerm(list.size() == 1);
				StringBuiltin value = (StringBuiltin) parseXML(list.get(0), definitionHelper);
				return new Constant("#Id", value.getValue());
			} else if (op.matches("\\.(Map|Bag|List|Set|K)") && (sort.equals("Bag") || sort.equals("List") || sort.equals("Map") || sort.equals("Set") || sort.equals("K"))) {
				assertXMLTerm(list.size() == 0);
                if (sort.equals("Bag")) {
                    return Bag.EMPTY;
                } else if (sort.equals("List")) {
                    return org.kframework.kil.List.EMPTY;
                } else if (sort.equals("Map")) {
                    return org.kframework.kil.Map.EMPTY;
                } else if (sort.equals("Set")) {
                    return org.kframework.kil.Set.EMPTY;
                } else {
                    // sort.equals("K")
                    return KSequence.EMPTY;
                }
			} else if (op.equals(".KList") && sort.equals(KSorts.KLIST)) {
				assertXMLTerm(list.size() == 0);
				return KList.EMPTY;
			} else if (op.equals("_`(_`)") && (sort.equals(KSorts.KITEM) || sort.equals("[KList]"))) {
				assertXMLTerm(list.size() == 2);
				Term child = parseXML(list.get(1), definitionHelper);
				if (!(child instanceof KList)) {
					List<Term> terms = new ArrayList<Term>();
					terms.add(child);
					child = new KList(terms);
				}
				return new KApp(parseXML(list.get(0), definitionHelper),child);
			} else if (sort.equals(KSorts.KLABEL) && list.size() == 0) {
				return KLabelConstant.of(StringUtil.unescapeMaude(op), definitionHelper);
			} else if (sort.equals(KSorts.KLABEL) && op.equals("#freezer_")) {
				assertXMLTerm(list.size() == 1);
				return new FreezerLabel(parseXML(list.get(0), definitionHelper));	
			} else if (op.equals("HOLE")) {
				assertXMLTerm(list.size() == 0 && sort.equals(KSorts.KITEM));
				//return new Hole(sort);
			    return Hole.KITEM_HOLE;
            } else {
				Set<String> conses = definitionHelper.labels.get(StringUtil.unescapeMaude(op));
				Set<String> validConses = new HashSet<String>();
				List<Term> possibleTerms = new ArrayList<Term>();
				assertXMLTerm(conses != null);
				for (String cons : conses) {
					Production p = definitionHelper.conses.get(cons);
					if (p.getSort().equals(sort) && p.getArity() == list.size()) {
						validConses.add(cons);
					}
				}
				assertXMLTerm(validConses.size() > 0);
				List<Term> contents = new ArrayList<Term>();
				for (Element elem : list) {
					contents.add(parseXML(elem, definitionHelper));
				}
				for (String cons : validConses) {
					possibleTerms.add(new TermCons(sort, cons, contents));
				}
				if (possibleTerms.size() == 1) {
					return possibleTerms.get(0);
				} else {
					return new Ambiguity(sort, possibleTerms);
				}
			}
		} catch (Exception e) {
			return new BackendTerm(sort, flattenXML(xml));
		}
	}

	public static String flattenXML(Element xml) {
		List<Element> children = XmlUtil.getChildElements(xml);
		if (children.size() == 0) {
			return xml.getAttribute("op");
		} else {
			String result = xml.getAttribute("op");
			String conn = "(";
			for (Element child : children) {
				result += conn;
				conn = ",";
				result += flattenXML(child);
			}
			result += ")";
			return result;
		}
	}

	private static String getSearchType(SearchType s) {
		if (s == SearchType.ONE) return "1";
		if (s == SearchType.PLUS) return "+";
		if (s == SearchType.STAR) return "*";
		if (s == SearchType.FINAL) return "!";
		return null;
	}

	public KRunResult<SearchResults> search(Integer bound, Integer depth,
										SearchType searchType, Rule pattern,
										Term cfg,
										RuleCompilerSteps compilationInfo)
			throws KRunExecutionException {
		String cmd = "set show command off ." + K.lineSeparator + setCounter() + "search ";
		if (bound != null && depth != null) {
			cmd += "[" + bound + "," + depth + "] ";
		} else if (bound != null) {
			cmd += "[" + bound + "] ";
		} else if (depth != null) {
			cmd += "[," + depth + "] ";
		}
		MaudeFilter maudeFilter = new MaudeFilter(definitionHelper);
		cfg.accept(maudeFilter);
		cmd += maudeFilter.getResult() + " ";
		MaudeFilter patternBody = new MaudeFilter(definitionHelper);
		pattern.getBody().accept(patternBody);
		String patternString = "=>" + getSearchType(searchType) + " " + patternBody.getResult();
		if (pattern.getCondition() != null) {
			MaudeFilter patternCondition = new MaudeFilter(definitionHelper);
			pattern.getCondition().accept(patternCondition);
			patternString += " such that " + patternCondition.getResult() + " = # true(.KList)";
		}
		cmd += patternString + " .";
		cmd += K.lineSeparator + "show search graph .";
		if (K.trace) {
			cmd = "set trace on ." + K.lineSeparator + cmd;
		}
		cmd += getCounter();
		executeKRun(cmd, K.io);
		try {
			SearchResults results;
			final List<SearchResult> solutions = parseSearchResults
					(pattern, compilationInfo);
			final boolean matches = patternString.trim().matches("=>[!*1+] " +
					"<_>_</_>\\(generatedTop, B:Bag, generatedTop\\)");
			results = new SearchResults(solutions, parseSearchGraph(), matches, definitionHelper);
			K.stateCounter += results.getGraph().getVertexCount();
			KRunResult<SearchResults> result = new KRunResult<SearchResults>(results);
			result.setRawOutput(FileUtil.getFileContent(K.maude_out));
			return result;
		} catch (Exception e) {
			throw new RuntimeException("Pretty-printer threw exception", e);
		}
	}

	private DirectedGraph<KRunState, Transition> parseSearchGraph() throws Exception {
		FileReader reader = new FileReader(K.maude_output);
		Scanner scanner = new Scanner(reader);
		scanner.useDelimiter("\n");
		FileWriter writer = new FileWriter(K.processed_maude_output);
		while (scanner.hasNext()) {
			String text = scanner.next();
			text = text.replaceAll("<data key=\"((rule)|(term))\">", "<data key=\"$1\"><![CDATA[");
			text = text.replaceAll("</data>", "]]></data>");
			writer.write(text, 0, text.length());
		}
		writer.close();
			
		File input = new File(K.processed_maude_output);
		Document doc = XmlUtil.readXML(input);
		NodeList list = null;
		Node nod = null;
		list = doc.getElementsByTagName("graphml");
		assertXML(list.getLength() == 1);
		nod = list.item(0);
		assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
		XmlUtil.serializeXML(nod, K.processed_maude_output);
		reader = new FileReader(K.processed_maude_output);
			
		Transformer<GraphMetadata, DirectedGraph<KRunState, Transition>> graphTransformer = new Transformer<GraphMetadata, DirectedGraph<KRunState, Transition>>() { 
			public DirectedGraph<KRunState, Transition> transform(GraphMetadata g) { 
				return new DirectedSparseGraph<KRunState, Transition>();
			}
		};
		Transformer<NodeMetadata, KRunState> nodeTransformer = new Transformer<NodeMetadata, KRunState>() {
			public KRunState transform(NodeMetadata n) {
				String nodeXmlString = n.getProperty("term");
				Element xmlTerm = XmlUtil.readXML(nodeXmlString).getDocumentElement();
				KRunState ret = parseElement(xmlTerm, definitionHelper);
				String id = n.getId();
				id = id.substring(1);
				ret.setStateId(Integer.parseInt(id) + K.stateCounter);
				return ret;
			}
		};
		Transformer<EdgeMetadata, Transition> edgeTransformer = new Transformer<EdgeMetadata, Transition>() {
			public Transition transform(EdgeMetadata e) {
				String edgeXmlString = e.getProperty("rule");
				Element elem = XmlUtil.readXML(edgeXmlString).getDocumentElement();
				String metadataAttribute = elem.getAttribute("metadata");
				Pattern pattern = Pattern.compile("([a-z]*)=\\((.*?)\\)");
				Matcher matcher = pattern.matcher(metadataAttribute);
				String location = null;
				String filename = null;
				while (matcher.find()) {
					String name = matcher.group(1);
					if (name.equals("location"))
						location = matcher.group(2);
					if (name.equals("filename"))
						filename = matcher.group(2);
				}
				if (location == null || location.equals("generated") || filename == null) {
					// we should avoid this wherever possible, but as a quick fix for the
					// superheating problem and to avoid blowing things up by accident when
					// location information is missing, I am creating non-RULE edges.
					String labelAttribute = elem.getAttribute("label");
					if (labelAttribute == null) {
						return new Transition(TransitionType.UNLABELLED, definitionHelper);
					} else {
						return new Transition(labelAttribute, definitionHelper);
					}
				}
				return new Transition(definitionHelper.locations.get(filename + ":(" + location + ")"), definitionHelper);
			}
		};

		Transformer<HyperEdgeMetadata, Transition> hyperEdgeTransformer = new Transformer<HyperEdgeMetadata, Transition>() {
			public Transition transform(HyperEdgeMetadata h) {
				throw new RuntimeException("Found a hyper-edge. Has someone been tampering with our intermediate files?");
			}
		};
				
		GraphMLReader2<DirectedGraph<KRunState, Transition>, KRunState, Transition> graphmlParser = new GraphMLReader2<DirectedGraph<KRunState, Transition>, KRunState, Transition>(reader, graphTransformer, nodeTransformer, edgeTransformer, hyperEdgeTransformer);
		return graphmlParser.readGraph();
	}

	private List<SearchResult> parseSearchResults(Rule pattern, RuleCompilerSteps compilationInfo) {
		List<SearchResult> results = new ArrayList<SearchResult>();
		File input = new File(K.maude_output);
		Document doc = XmlUtil.readXML(input);
		NodeList list = null;
		Node nod = null;
		list = doc.getElementsByTagName("search-result");
		for (int i = 0; i < list.getLength(); i++) {
			nod = list.item(i);
			assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
			Element elem = (Element) nod;
			if (elem.getAttribute("solution-number").equals("NONE")) {
				continue;
			}
			int stateNum = Integer.parseInt(elem.getAttribute("state-number"));
			Map<String, Term> rawSubstitution = new HashMap<String, Term>();
			NodeList assignments = elem.getElementsByTagName("assignment");
			for (int j = 0; j < assignments.getLength(); j++) {
				nod = assignments.item(j);
				assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
				elem = (Element) nod;
				List<Element> child = XmlUtil.getChildElements(elem);
				assertXML(child.size() == 2);
				Term result = parseXML(child.get(1), definitionHelper);
				rawSubstitution.put(child.get(0).getAttribute("op"), result);
			}

			try {
				Term rawResult = (Term)pattern.getBody().accept(new SubstitutionFilter(rawSubstitution, definitionHelper));
				KRunState state = new KRunState(rawResult, definitionHelper);
				state.setStateId(stateNum + K.stateCounter);
				SearchResult result = new SearchResult(state, rawSubstitution, compilationInfo, definitionHelper);
				results.add(result);
			} catch (TransformerException e) {
				e.report(); //this should never happen, so I want it to blow up
			}
		}
		list = doc.getElementsByTagName("result");
		nod = list.item(1);
		parseCounter(nod);
		return results;		
	}

	public KRunResult<DirectedGraph<KRunState, Transition>> modelCheck(Term formula, Term cfg) throws KRunExecutionException {
		MaudeFilter formulaFilter = new MaudeFilter(definitionHelper);
		formula.accept(formulaFilter);
		MaudeFilter cfgFilter = new MaudeFilter(definitionHelper);
		cfg.accept(cfgFilter);

		String cmd = "mod MCK is" + K.lineSeparator + " including " + K.main_module + " ." + K.lineSeparator + K.lineSeparator + " op #initConfig : -> Bag ." + K.lineSeparator + K.lineSeparator + " eq #initConfig  =" + K.lineSeparator + cfgFilter.getResult() + " ." + K.lineSeparator + "endm" + K.lineSeparator + K.lineSeparator + "red" + K.lineSeparator + "_`(_`)(('modelCheck`(_`,_`)).KLabel,_`,`,_(_`(_`)(Bag2KLabel(#initConfig),.KList)," + K.lineSeparator + formulaFilter.getResult() + ")" + K.lineSeparator + ") .";
		executeKRun(cmd, false);
		KRunResult<DirectedGraph<KRunState, Transition>> result = parseModelCheckResult();
		result.setRawOutput(FileUtil.getFileContent(K.maude_out));
		return result;
	}

	private KRunResult<DirectedGraph<KRunState, Transition>> parseModelCheckResult() {
		File input = new File(K.maude_output);
		Document doc = XmlUtil.readXML(input);
		NodeList list = null;
		Node nod = null;
		list = doc.getElementsByTagName("result");
		assertXML(list.getLength() == 1);
		nod = list.item(0);
		assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
		Element elem = (Element) nod;
		List<Element> child = XmlUtil.getChildElements(elem);
		assertXML(child.size() == 1);
		String sort = child.get(0).getAttribute("sort");
		String op = child.get(0).getAttribute("op");
		assertXML(op.equals("_`(_`)") && sort.equals(KSorts.KITEM));
		child = XmlUtil.getChildElements(child.get(0));
		assertXML(child.size() == 2);
		sort = child.get(0).getAttribute("sort");
		op = child.get(0).getAttribute("op");
		assertXML(op.equals("#_") && sort.equals(KSorts.KLABEL));
		sort = child.get(1).getAttribute("sort");
		op = child.get(1).getAttribute("op");
		assertXML(op.equals(".KList") && sort.equals(KSorts.KLIST));
		child = XmlUtil.getChildElements(child.get(0));
		assertXML(child.size() == 1);
		elem = child.get(0);
		if (elem.getAttribute("op").equals("true") && elem.getAttribute("sort").equals("#Bool")) {
			return new KRunResult<DirectedGraph<KRunState, Transition>>(null);
		} else {
			sort = elem.getAttribute("sort");
			op = elem.getAttribute("op");
			assertXML(op.equals("LTLcounterexample") && sort.equals("#ModelCheckResult"));
			child = XmlUtil.getChildElements(elem);
			assertXML(child.size() == 2);
			List<MaudeTransition> initialPath = new ArrayList<MaudeTransition>();
			List<MaudeTransition> loop = new ArrayList<MaudeTransition>();
			parseCounterexample(child.get(0), initialPath, definitionHelper);
			parseCounterexample(child.get(1), loop, definitionHelper);
			DirectedGraph<KRunState, Transition> graph = new DirectedOrderedSparseMultigraph<KRunState, Transition>();
			Transition edge = null;
			KRunState vertex = null;
			for (MaudeTransition trans : initialPath) {
				graph.addVertex(trans.state);
				if (edge != null) {
					graph.addEdge(edge, vertex, trans.state);
				}
				edge = trans.label;
				vertex = trans.state;
			}
			for (MaudeTransition trans : loop) {
				graph.addVertex(trans.state);
				graph.addEdge(edge, vertex, trans.state);
				edge = trans.label;
				vertex = trans.state;
			}
			graph.addEdge(edge, vertex, loop.get(0).state);
			
			return new KRunResult<DirectedGraph<KRunState, Transition>>(graph);
		}
	}

	private static class MaudeTransition {
		public KRunState state;
		public Transition label;

		public MaudeTransition(KRunState state, Transition label) {
			this.state = state;
			this.label = label;
		}
	}

	private static void parseCounterexample(Element elem, List<MaudeTransition> list, DefinitionHelper definitionHelper) {
		String sort = elem.getAttribute("sort");
		String op = elem.getAttribute("op");
		List<Element> child = XmlUtil.getChildElements(elem);
		if (sort.equals("#TransitionList") && op.equals("_LTL_")) {
			assertXML(child.size() >= 2);
			for (Element e : child) {
				parseCounterexample(e, list, definitionHelper);
			}
		} else if (sort.equals("#Transition") && op.equals("LTL`{_`,_`}")) {
			assertXML(child.size() == 2);
			Term t = parseXML(child.get(0), definitionHelper);
		
			List<Element> child2 = XmlUtil.getChildElements(child.get(1));
			sort = child.get(1).getAttribute("sort");
			op = child.get(1).getAttribute("op");
			assertXML(child2.size() == 0 && (sort.equals("#Qid") || sort.equals("#RuleName")));
			String label = op;
			Transition trans;
			if (sort.equals("#RuleName") && op.equals("UnlabeledLtl")) {
				trans = new Transition(TransitionType.UNLABELLED, definitionHelper);
			} else {
				trans = new Transition(label, definitionHelper);
			}
			list.add(new MaudeTransition(new KRunState(t, definitionHelper), trans));
		} else if (sort.equals("#TransitionList") && op.equals("LTLnil")) {
			assertXML(child.size() == 0);
		} else {
			assertXML(false);
		}
	}

	public KRunDebugger debug(Term cfg) throws KRunExecutionException {
		return new KRunApiDebugger(this, cfg, definitionHelper);
	}

	public KRunDebugger debug(SearchResults searchResults) {
		return new KRunApiDebugger(this, searchResults.getGraph());
	}
}
