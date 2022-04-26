package dpcollector.manager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import analysisutils.Globals;
import analysisutils.Log;
import dpcollector.Constants;
import dpcollector.IoTDataClassifier;
import dpcollector.Utils;
import soot.util.MultiMap;

public class DataBlockManager {
	private static boolean usedContextualURL = true;
	public static List<String> CATEGORIES = Arrays.asList("java.util.Map", "java.util.LinkedHashMap",
			"java.util.HashMap", "org.json.JSONObject", "com.google.gson.Gson", /*"java.lang.StringBuilder",*/
			"android.content.SharedPreferences", "org.apache.http.NameValuePair",
			"org.apache.http.message.BasicNameValuePair", "okhttp3.FormBody$Builder");

	private static DataBlockManager singleton;
	private Map<String, DataBlock> dataBlockMapping;
	
	public static DataBlockManager getInstance() {
		if (singleton == null) {
			singleton = new DataBlockManager();
		}
		return singleton;
	}
	
	private DataBlockManager() {
		this.dataBlockMapping = new HashMap<String, DataBlock>();
	}
	
	public Map<String, DataBlock> getDataBlockMapping() {
		return dataBlockMapping;
	}
	
	private void contextualURLMatch() {
		MultiMap<String, String> method2URLs = MyResourceManager.getInstance().getMethod2URLs();
		MultiMap<String, String> clazz2URLs = MyResourceManager.getInstance().getClazz2URLs();
		MultiMap<String, String> callerMap = MyResourceManager.getInstance().getCallerMap();

		for (DataBlock block : this.dataBlockMapping.values()) {
			if (block.urls.size() < 1) {
				if (method2URLs.containsKey(block.hostingMethodSig)) {
					block.addUrls(method2URLs.get(block.hostingMethodSig));
				}
			}

			if (block.urls.size() < 1) {
				for (String callerSig : callerMap.get(block.hostingMethodSig)) {
					block.addUrls(method2URLs.get(callerSig));
				}
			}

			if (block.urls.size() < 1) {
				if (clazz2URLs.containsKey(block.clazzSig)) {
					block.addUrls(clazz2URLs.get(block.clazzSig));
				}
			}
		}
	}

	public void postProcessing() {
		if (DataBlockManager.usedContextualURL == false) {
			contextualURLMatch();
			DataBlockManager.usedContextualURL = true;
		}
		
		// classifying iot data
		this.dataClassification();
	}

	private void dataClassification() {
		String[] dbSigOrder = new String[this.dataBlockMapping.size()];
		int idx = 0;

		StringBuilder sb = new StringBuilder("");
		for (String dbSig : this.dataBlockMapping.keySet()) {
			DataBlock db = this.dataBlockMapping.get(dbSig);
			if (db.values.size() >= Constants.MIN_DATABLOCK_VALUES) {
				String joinValues = StringUtils.join(db.values, ' ');
				joinValues = StringUtils.join(Utils.cleanTextContent(joinValues), ' ');
				dbSigOrder[idx++] = dbSig;
				sb.append(joinValues).append("####");
			}
		}

		String ret = IoTDataClassifier.bulkPythonClassify(sb.toString());
		idx = 0;
		String[] labels = ret.split("####");
		for (String label : labels) {
			if (label.equals("1")) {
				this.dataBlockMapping.get(dbSigOrder[idx]).isIotML = true;
			}
			idx += 1;
		}
	}

	public void dumpJSONLineByLine() {
		// dump data blocks
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		for (DataBlock db : this.dataBlockMapping.values()) {
			if (db.values.size() >= Constants.MIN_DATABLOCK_VALUES) {
				Log.dumpln(Globals.LOG_FILE, gson.toJson(db.toJSON()));
			}
		}
	}

	public class DataBlock {
		public String hostingMethodSig;
		public String clazzSig;
		public String category;
		public Set<String> values;
		public Set<String> urls;
		public Set<String> urls3p;
		private Set<String> leakAPIs;
		public boolean isIotML;
		public Set<String> stmts;

		public DataBlock(String hostingMethodSig, String clazzSig, String dataHandlingClass) {
			this.hostingMethodSig = hostingMethodSig;
			this.clazzSig = clazzSig;
			this.category = dataHandlingClass;
			this.values = new HashSet<String>();
			this.urls = new HashSet<String>();
			this.urls3p = new HashSet<String>();
			this.leakAPIs = new HashSet<String>();
			this.isIotML = false;
			this.stmts = new HashSet<String>();
		}
		
		public void addLeakAPI(String apiSig) {
			this.leakAPIs.add(apiSig);
		}

		// only consider "value" that only contains alphanumeric characters and "_"
		public void addValue(String value) {
			String nvalue = value.replace("\"", "");
			if (nvalue.length() < Constants.MIN_NONOBFUSCATED_LENGTH) {
				return;
			}
			Pattern p = Pattern.compile("[^a-zA-Z0-9_]");
			if (p.matcher(nvalue).find()) {
				return;
			}
			this.values.add(nvalue);
		}

		public void addUrls(Set<String> urls) {
			for (String url : urls) {
				// remove urls such as xmlpull.org
				if (Utils.containsAnyKeyword(url, Constants.NO_INTEREST_URLS)) {
					continue;
				}

				this.urls.add(url);
				if (AppMetaImporter.getInstance().isURL3P(url, Globals.PACKAGE_NAME)) {
					this.urls3p.add(url);
				}
			}
		}
		
		public void addStmt(String stmt) {
			this.stmts.add(stmt);
		}

		public JsonObject toJSON() {
			JsonObject jObj = new JsonObject();
			jObj.addProperty("type", "dtblk");
			jObj.addProperty("host", this.hostingMethodSig);
			jObj.addProperty("category", this.category);
			jObj.addProperty("isIotML", this.isIotML);
			jObj.add("values", Utils.toJsonArray(this.values));
			jObj.add("urls", Utils.toJsonArray(this.urls));
			jObj.add("urls3p", Utils.toJsonArray(this.urls3p));
			jObj.add("leakAPIs", Utils.toJsonArray(this.leakAPIs));

			return jObj;
		}
		
		public String toString() {
			return String.format("%s -- %s", this.hostingMethodSig, this.category);
		}
	}
}