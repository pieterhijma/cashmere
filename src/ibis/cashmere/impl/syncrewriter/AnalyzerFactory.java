package ibis.cashmere.impl.syncrewriter;


class AnalyzerFactory {


    static Analyzer createAnalyzer (String analyzerName) throws 
	ClassNotFoundException, IllegalAccessException, InstantiationException {

	    ClassLoader classLoader = AnalyzerFactory.class.getClassLoader();
	    Class<?> analyzerClass = classLoader.loadClass(
		    "ibis.cashmere.impl.syncrewriter.analyzer." + analyzerName);

	    return (Analyzer) analyzerClass.newInstance();
	} 
}
