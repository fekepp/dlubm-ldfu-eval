package edu.kit.aifb.ldbwebservice;

import org.apache.marmotta.commons.vocabulary.LDP;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;

public class STEP {
	
	public static final String NAMESPACE = "http://step.aifb.kit.edu/";

    /** {@code step} **/
    public static final String PREFIX = "step";

	public static final URI LinkedDataWebService;
	public static final URI StartAPI;
	public static final URI Output;
	
	public static final URI hasWebService;
	public static final URI hasProgram;
	public static final URI hasValue;

	public static final URI hasStartAPI;

	
    static {
        ValueFactory factory = ValueFactoryImpl.getInstance();
        
        // Classes:

        LinkedDataWebService = factory.createURI(STEP.NAMESPACE, "LinkedDataWebService");
        
        StartAPI =  factory.createURI(STEP.NAMESPACE, "StartAPI");
        
        Output = factory.createURI(STEP.NAMESPACE, "Output");
        
        
        // Predicates:
        
        hasWebService = factory.createURI(STEP.NAMESPACE, "hasWebService");

        hasProgram = factory.createURI(STEP.NAMESPACE, "hasProgram");
        
        hasValue = factory.createURI(STEP.NAMESPACE, "hasValue");
        
        hasStartAPI = factory.createURI(STEP.NAMESPACE, "hasStartAPI");
    }

}
