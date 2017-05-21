package edu.kit.aifb.ldbwebservice;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

import edu.kit.aifb.datafu.Binding;
import edu.kit.aifb.datafu.ConstructQuery;
import edu.kit.aifb.datafu.Origin;
import edu.kit.aifb.datafu.Program;
import edu.kit.aifb.datafu.Query;
import edu.kit.aifb.datafu.SelectQuery;
import edu.kit.aifb.datafu.consumer.impl.BindingConsumerCollection;
import edu.kit.aifb.datafu.io.origins.InternalOrigin;
import edu.kit.aifb.datafu.parser.ProgramConsumerImpl;
import edu.kit.aifb.datafu.parser.QueryConsumerImpl;
import edu.kit.aifb.datafu.parser.notation3.Notation3Parser;
import edu.kit.aifb.datafu.parser.notation3.ParseException;
import edu.kit.aifb.datafu.parser.sparql.SparqlParser;
import edu.kit.aifb.datafu.web.api.Instance;

public class LDFU_TestClass {

	public static final String PROGRAM_DISTANCE = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> . "
			+ "@prefix qrl: <http://www.aifb.kit.edu/project/ld-retriever/qrl#> . "
			+ "@prefix math: <http://www.w3.org/2000/10/swap/math#> . " + "@prefix ldfu: <http://ldfu#> . "
			+ "@prefix nirest: <http://vocab.arvida.de/2014/02/nirest/vocab#> . "
			+ "@prefix scenario: <http://scenario#> . " + "{ " + "<http://coordinate> <http://x> ?x . "
			+ "<http://coordinate> <http://y> ?y . " + "<http://coordinate> <http://z> ?z . "
			+ "(?x \"2\") math:exponentiation ?x_ex . " + "(?y \"2\") math:exponentiation ?y_ex . "
			+ "(?z \"2\") math:exponentiation ?z_ex . " + "(?x_ex ?y_ex ?z_ex) math:sum ?sum . "
			+ "?sum math:sqrt ?square_root . " + "} => { " + "<http://coordinate> <http://distance> ?square_root . "
			+ "} .";

	public static final String PROGRAM_TRIPLE = "<http://coordinate> <http://x> \"1\" . "
			+ "<http://coordinate> <http://y> \"2\" . " + "<http://coordinate> <http://z> \"3\" . ";

	public static final String PROGRAM_TEST = "{ <http://coordinate> <http://x> ?x . } " + " => "
			+ "{ <http://test> <http://test> ?x . } .";

	public static final String QUERY_CONSTRUCT_SPO = "CONSTRUCT { ?s ?p ?o . } WHERE { ?s ?p ?o . }";

	public static void main(String[] args)
			throws ParseException, edu.kit.aifb.datafu.parser.sparql.ParseException, InterruptedException {

		// Parse programs
		Program programTriple = getProgramTriple();
		Program programTest = getProgramTest();
		Program programDistance = getProgramDistance();

		// Parse query
		Origin origin = new InternalOrigin("queryOrigin");
		QueryConsumerImpl queryConsumer = new QueryConsumerImpl(origin);
		SparqlParser parser = new SparqlParser(new ByteArrayInputStream(QUERY_CONSTRUCT_SPO.getBytes()));
		parser.parse(queryConsumer, origin);
		Set<Query> queries = new HashSet<Query>();
		Set<ConstructQuery> constructQueries = queryConsumer.getConstructQueries();
		queries.addAll(constructQueries);
		Set<SelectQuery> selectQueries = queryConsumer.getSelectQueries();
		queries.addAll(selectQueries);

		// Create instance
		Instance instance = new Instance();
		instance.setDelay(1000);

		// Add program to instance
		instance.putProgram("programTriple", programTriple);
		instance.putProgram("programTest", programTest);
		instance.putProgram("programDistance", programDistance);
		instance.putConstructQueries("constructQueries", constructQueries);

		while (true) {
			// BindingConsumerCollection bindingConsumerCollection =
			// instance.evaluateQueries(queries);
			BindingConsumerCollection bindingConsumerCollection = instance
					.getConstructQueryConsumer("constructQueries");
			for (Binding binding : bindingConsumerCollection.getCollection()) {
				System.out.println(binding.getNodes());
			}
			Thread.sleep(1000);
		}

	}

	public static Program getProgramTriple() throws ParseException {
		Origin origin = new InternalOrigin("programOriginTriple");
		ProgramConsumerImpl programConsumer = new ProgramConsumerImpl(origin);
		Notation3Parser notation3Parser = new Notation3Parser(new ByteArrayInputStream(PROGRAM_TRIPLE.getBytes()));
		notation3Parser.parse(programConsumer, origin);
		return programConsumer.getProgram(origin);
	}

	public static Program getProgramTest() throws ParseException {
		Origin origin = new InternalOrigin("programOriginTest");
		ProgramConsumerImpl programConsumer = new ProgramConsumerImpl(origin);
		Notation3Parser notation3Parser = new Notation3Parser(new ByteArrayInputStream(PROGRAM_TEST.getBytes()));
		notation3Parser.parse(programConsumer, origin);
		return programConsumer.getProgram(origin);
	}

	public static Program getProgramDistance() throws ParseException {
		Origin origin = new InternalOrigin("programOriginDistance");
		ProgramConsumerImpl programConsumer = new ProgramConsumerImpl(origin);
		Notation3Parser notation3Parser = new Notation3Parser(new ByteArrayInputStream(PROGRAM_DISTANCE.getBytes()));
		notation3Parser.parse(programConsumer, origin);
		return programConsumer.getProgram(origin);
	}

}