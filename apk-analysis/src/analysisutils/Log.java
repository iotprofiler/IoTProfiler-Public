package analysisutils;

import java.io.*;

public class Log {
	public static void init(String filename) {
		File file = new File(filename);
		if (file.exists()) {
			file.delete();
		}
	}
	
	public static void dumpln(String filename, String msg) {
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename, true)));
			out.println(msg);
			out.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}