package dpcollector;

import java.util.Arrays;
import java.util.List;

public class Constants {
	public static int MIN_DATABLOCK_VALUES = 4;
	public static int MIN_NONOBFUSCATED_LENGTH = 4;
	public static int BACKWARD_STEPS = 3;

	public static List<String> NO_INTEREST_URLS = Arrays.asList("play.google.com", "tempuri.org", "127.0.0.1",
			"0.0.0.0", "xmlpull.org", "schemas.android.com", "www.example.com", "schema.org", "www.w3.org",
			"www.googleapis.com", "plus.google.com");
	
	public static List<String> INTERESTING_TYPES = Arrays.asList("boolean", "boolean[]", "byte", "byte[]", "char",
			"char[]", "com.google.gson.Gson", "double", "double[]", "float", "float[]", "int", "int[]",
			"java.lang.Boolean", "java.lang.Boolean[]", "java.lang.Byte", "java.lang.Byte[]", "java.lang.Character",
			"java.lang.Character[]", "java.lang.CharSequence", "java.lang.CharSequence[]", "java.lang.Double",
			"java.lang.Double[]", "java.lang.Float", "java.lang.Float[]", "java.lang.Integer", "java.lang.Integer[]",
			"java.lang.Long", "java.lang.Long[]", "java.lang.Object", "java.lang.Object[]", "java.lang.Short",
			"java.lang.Short[]", "java.lang.String", "java.lang.String[]", "java.lang.StringBuilder",
			"java.nio.ByteBuffer", "java.util.ArrayList", "java.util.Calendar",
			"java.util.concurrent.ConcurrentHashMap", "java.util.Date", "java.util.HashMap", "java.util.HashSet",
			"java.util.Hashtable", "java.util.LinkedHashMap", "java.util.LinkedList", "java.util.Map", "java.util.UUID",
			"java.util.Vector", "long", "long[]", "org.json.JSONArray", "org.json.JSONObject", "short", "short[]");

}