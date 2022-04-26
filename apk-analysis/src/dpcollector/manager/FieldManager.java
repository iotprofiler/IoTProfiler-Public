package dpcollector.manager;

import java.util.*;
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
import soot.SootField;
import soot.SootMethod;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class FieldManager {
	private static FieldManager singleton;
	private Map<String, FieldBean> fieldMapping;
	private MultiMap<String, FieldBean> class2Fields;

	private FieldManager() {
		this.fieldMapping = new HashMap<String, FieldBean>();
		this.class2Fields = new HashMultiMap<String, FieldBean>();
	}

	public static FieldManager getInstance() {
		if (singleton == null) {
			singleton = new FieldManager();
		}
		return singleton;
	}

	public Map<String, FieldBean> getFieldMapping() {
		return fieldMapping;
	}

	public MultiMap<String, FieldBean> getClass2Fields() {
		return class2Fields;
	}

	public void addField(SootField field) {
		if (!this.fieldMapping.containsKey(field.getSignature())) {
			FieldBean fb = new FieldBean(field);
			this.fieldMapping.put(field.getSignature(), fb);
			this.class2Fields.put(field.getDeclaringClass().getName(), fb);
		}
	}

	public void putFieldSetter(String fieldSig, SootMethod setterMethod) {
		this.fieldMapping.get(fieldSig).addSetterMethod(setterMethod);
	}

	public void putFieldGetter(String fieldSig, SootMethod getterMethod) {
		this.fieldMapping.get(fieldSig).addGetterMethod(getterMethod);
	}

	public FieldBean getFieldByGetterSig(String getterSig) {
		for (FieldBean bean : this.fieldMapping.values()) {
			if (bean.getGetterSigs().contains(getterSig)) {
				return bean;
			}
		}
		return null;
	}

	public void postProcessing() {
		this.dataClassification();
	}

	private void dataClassification() {
		String[] classSigOrder = new String[this.class2Fields.size()];
		int idx = 0;

		StringBuilder sb = new StringBuilder("");
		for (String classSig : this.class2Fields.keySet()) {
			if (this.class2Fields.get(classSig).size() >= Constants.MIN_DATABLOCK_VALUES) {
				StringBuilder joinValues = new StringBuilder("");
				for (FieldBean bean : this.class2Fields.get(classSig)) {
					if (bean.getField().getName().length() >= Constants.MIN_NONOBFUSCATED_LENGTH) {
						joinValues.append(bean.getField().getName()).append(" ");
					} else if (bean.getExtras().size() > 0) {
						joinValues.append(StringUtils.join(bean.getExtras(), ' ')).append(' ');
					}
				}

				if (joinValues.length() > 0) {
					classSigOrder[idx++] = classSig;
					sb.append(StringUtils.join(Utils.cleanTextContent(joinValues.toString()), ' ')).append("####");
				}
			}
		}

		String ret = IoTDataClassifier.bulkPythonClassify(sb.toString());
		idx = 0;
		String[] labels = ret.split("####");
		for (String label : labels) {
			if (label.equals("1")) {
				String classSig = classSigOrder[idx];
				for (FieldBean bean : this.class2Fields.get(classSig)) {
					bean.isIotML = true;
				}
			}
			idx += 1;
		}
	}

	public void dumpJSONLineByLine() {
		/* print a class as a whole */
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();

		for (String classSig : this.class2Fields.keySet()) {
			JsonObject jObj = new JsonObject();
			jObj.addProperty("type", "dtblk");
			jObj.addProperty("host", classSig);
			jObj.addProperty("category", "class_fields");
			boolean isIotML = false;

			Set<String> urls = new HashSet<String>();
			Set<String> urls3p = new HashSet<String>();
			Set<String> values = new HashSet<String>();
			Set<String> leakAPIs = new HashSet<String>();

			for (FieldBean bean : this.class2Fields.get(classSig)) {
				isIotML = bean.isIotML || isIotML;
				urls.addAll(bean.urls);
				urls3p.addAll(urls3p);

				if (bean.getField().getName().length() >= Constants.MIN_NONOBFUSCATED_LENGTH) {
					values.add(bean.getField().getName());
				} else {
					values.addAll(bean.getExtras());
				}
				
				leakAPIs.addAll(bean.leakAPIs);
			}

			jObj.addProperty("isIotML", isIotML);
			jObj.add("values", Utils.toJsonArray(values));
			jObj.add("urls", Utils.toJsonArray(urls));
			jObj.add("urls3p", Utils.toJsonArray(urls3p));
			jObj.add("leakAPIs", Utils.toJsonArray(leakAPIs));

			if (values.size() >= Constants.MIN_DATABLOCK_VALUES) {
				Log.dumpln(Globals.LOG_FILE, gson.toJson(jObj));
			}
		}
	}

	public class FieldBean {
		private SootField field;
		private String fieldSig;
		private Set<String> getterSigs;
		private Set<String> setterSigs;
		private Set<String> extras;
		private Set<String> leakAPIs;
		private Set<String> urls;
		private Set<String> urls3p;
		private boolean isIotML;

		public FieldBean(SootField field) {
			this.field = field;
			this.fieldSig = this.field.getSignature();
			this.getterSigs = new HashSet<String>();
			this.setterSigs = new HashSet<String>();
			this.extras = new HashSet<String>();
			this.leakAPIs = new HashSet<String>();
			this.urls = new HashSet<String>();
			this.urls3p = new HashSet<String>();
			this.isIotML = false;
		}

		public void addLeakAPI(String apiSig) {
			this.leakAPIs.add(apiSig);
		}

		public void addUrls(Set<String> urls) {
			for (String url : urls) {
				// some urls like xmlpull.org are not important at all
				if (Utils.containsAnyKeyword(url, Constants.NO_INTEREST_URLS)) {
					continue;
				}

				this.urls.add(url);
				if (AppMetaImporter.getInstance().isURL3P(url, Globals.PACKAGE_NAME)) {
					this.urls3p.add(url);
				}
			}
		}

		public boolean isIotML() {
			return isIotML;
		}

		public void setIotML(boolean isIotML) {
			this.isIotML = isIotML;
		}

		public boolean flowsOut() {
			return this.leakAPIs.size() > 0;
		}

		public SootField getField() {
			return field;
		}

		public Set<String> getGetterSigs() {
			return getterSigs;
		}

		public Set<String> getSetterSigs() {
			return setterSigs;
		}

		public String getFieldSig() {
			return this.fieldSig;
		}

		public void addGetterMethod(SootMethod method) {
			this.getterSigs.add(method.getSignature());

			// if this field is obfuscated, getters and setters are used to describe the
			// field
			if (this.field.getName().length() < Constants.MIN_NONOBFUSCATED_LENGTH) {
				String mn = method.getName();
				if (mn.contains("access$")) {
					return;
				}
				if (mn.startsWith("get")) {
					mn = mn.substring("get".length());
				}
				this.addExtra(mn);
			}
		}

		public void addSetterMethod(SootMethod method) {
			this.setterSigs.add(method.getSignature());

			// if this field is obfuscated, getters and setters are used to describe the
			// field
			if (this.field.getName().length() < Constants.MIN_NONOBFUSCATED_LENGTH) {
				String mn = method.getName();
				if (mn.contains("access$")) {
					return;
				}
				if (mn.startsWith("set")) {
					mn = mn.substring("set".length());
				}
				this.addExtra(mn);
			}
		}

		public void addExtra(String extra) {
			if (extra.length() < Constants.MIN_NONOBFUSCATED_LENGTH || !Pattern.compile(".*[a-z].*").matcher(extra).matches()) {
				return;
			}

			this.extras.add(extra);
		}

		public Set<String> getExtras() {
			return this.extras;
		}
	}
}
