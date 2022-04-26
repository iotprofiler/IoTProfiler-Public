package dpcollector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;

public class Utils {
	public static int commonDotsInPrefix(String phrase1, String phrase2) {
		int dotCnt = 0;
		int minLength = Math.min(phrase1.length(), phrase2.length());
		for (int i = 0; i < minLength; i++) {
			if (phrase1.charAt(i) != phrase2.charAt(i)) {
				break;
			}

			if (phrase1.charAt(i) == '.') {
				dotCnt += 1;
			}
		}

		return dotCnt;
	}

	public static String[] cleanTextContent(String text) {
		// strips off all non-ASCII characters
		text = text.replaceAll("[^\\x00-\\x7F]", " ");

		// erases all the ASCII control characters
		text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");

		// removes non-printable characters from Unicode
		text = text.replaceAll("\\p{C}", " ");

		text = text.replace("'", "").replace("\"", "");

		return text.split("[^\\w]+");
	}

	public static JsonArray toJsonArray(Collection<String> values) {
		JsonArray valueArr = new JsonArray();
		for (String value : values) {
			valueArr.add(value);
		}
		return valueArr;
	}

	public static boolean containsAnyKeyword(String str, Collection<String> keywords) {
		String lstr = str.toLowerCase();
		for (String k : keywords) {
			if (lstr.contains(k)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isIP(String ip) {
		try {
			if (ip == null || ip.isEmpty()) {
				return false;
			}

			String[] parts = ip.split("\\.");
			if (parts.length != 4) {
				return false;
			}

			for (String s : parts) {
				int i = Integer.parseInt(s);
				if ((i < 0) || (i > 255)) {
					return false;
				}
			}
			if (ip.endsWith(".")) {
				return false;
			}

			return true;
		} catch (NumberFormatException nfe) {
			return false;
		}
	}

	public static boolean isURL(String str) {
		Pattern p = Pattern.compile(
				"^((https?|ftp|mqtts?|)://|(www|ftp)\\.)?([a-z0-9-%]+(\\.[a-z0-9-%]+)+)(:([0-9]+|%d))?([/?].*)?$");
		Matcher m = p.matcher(str);
		if (m.find()) {
			if (Utils.isIP(str) == true) {
				return true;
			}

			try {
				new URL(str);
				return true;
			} catch (MalformedURLException e) {
			}
		}

		return false;
	}

	private static boolean isLocal(String url) {
		return url.contains("192.168.") || url.startsWith("239.") || url.contains("127.0.0.1") || url.contains("8.8.")
				|| url.contains("0.0.") || url.contains("255.255.");
	}

	public static boolean hasDiffDomain(String url1, String url2) {
		if (Utils.isLocal(url1) || Utils.isLocal(url2)) {
			return false;
		}

		String domain1 = url1;
		String domain2 = url2;

		try {
			domain1 = new URL(url1).getHost();
		} catch (MalformedURLException e) {
		}

		try {
			domain2 = new URL(url2).getHost();
		} catch (MalformedURLException e) {
		}

		List<String> domain1List = Arrays.asList(domain1.split("\\."));
		Collections.reverse(domain1List);
		domain1 = String.join(".", domain1List);

		List<String> domain2List = Arrays.asList(domain2.split("\\."));
		Collections.reverse(domain2List);
		domain2 = String.join(".", domain2List);

		return Utils.commonDotsInPrefix(domain1, domain2) < 2;
	}

	public static List<String> readLines(String fileName) {
		List<String> lines = new ArrayList<String>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(fileName));
			String line = reader.readLine();
			while (line != null) {
				lines.add(line);
				line = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			throw new RuntimeException("Unexpected IO error on " + fileName, e);
		}
		return lines;
	}
}
