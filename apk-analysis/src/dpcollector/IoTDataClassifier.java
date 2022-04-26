package dpcollector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import analysisutils.Globals;

public class IoTDataClassifier {	
	public static String bulkPythonClassify(String bulkString) {
		String pythonScriptPath = Globals.CONFIG_DIR + "IoT-Privacy/src/predict.py";
		
		ProcessBuilder processBuilder = new ProcessBuilder();
		if (Globals.RUN_IN_ECLIPSE) {
			processBuilder.command("bash", "-c", String.format("%s %s '%s'", System.getenv("pyPath"), pythonScriptPath, bulkString));
		} else {
			processBuilder.command("bash", "-c", String.format("%s %s '%s'", System.getProperty("pyPath"), pythonScriptPath, bulkString));
		}
		
		StringBuilder retSb = new StringBuilder("");
		Process pr;
		try {
			pr = processBuilder.start();
			BufferedReader bfr = new BufferedReader(new InputStreamReader(pr.getInputStream()));
			String line = "";
			while ((line = bfr.readLine()) != null) {
				retSb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("classification result: " + retSb);
		
		return retSb.toString();
	}	
}
