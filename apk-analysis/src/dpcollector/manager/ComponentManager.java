package dpcollector.manager;

import analysisutils.Globals;
import dpcollector.Utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.jimple.infoflow.android.axml.AXmlAttribute;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

public class ComponentManager {
	private static ComponentManager singleton;

	private Map<String, ComponentBean> compMap;

	enum COMPONENT_TYPE {
		ACTIVITY, SERVICE, RECEIVER, PROVIDER;
	}

	private ComponentManager() {
		this.compMap = new HashMap<>();
	}

	public static ComponentManager getInstance() {
		if (singleton == null) {
			singleton = new ComponentManager();
		}
		return singleton;
	}

	public ComponentBean getComponent(String name) {
		return this.compMap.get(name);
	}

	public boolean hasComponent(String name) {
		return this.compMap.containsKey(name);
	}

	public Set<String> getComponentNames() {
		return compMap.keySet();
	}

	private void parseComponent(AXmlNode nd, ComponentBean comp) {
		Map<String, AXmlAttribute<?>> attributes = nd.getAttributes();
		for (String k : attributes.keySet()) {
			if ("name".equals(k)) {
				String componentName = (String) ((AXmlAttribute<?>) attributes.get(k)).getValue();
				if (componentName.startsWith(".")) {
					componentName = String.valueOf(Globals.PACKAGE_NAME) + componentName;
				} else if (!componentName.contains(".")) {
					componentName = String.valueOf(Globals.PACKAGE_NAME) + "." + componentName;
				}
				comp.setName(componentName);
			}
			if ("exported".equals(k)) {
				comp.setExported(((Boolean) ((AXmlAttribute<?>) attributes.get(k)).getValue()).booleanValue());
			}

			if ("permission".equals(k)) {
				comp.setPermission((String) ((AXmlAttribute<?>) attributes.get(k)).getValue());
			}
		}
		List<AXmlNode> children = nd.getChildren();
		for (AXmlNode node : children) {
			if ("intent-filter".equals(node.getTag())) {
				for (AXmlNode child : node.getChildren()) {
					if ("action".equals(child.getTag())) {
						for (String actionName : child.getAttributes().keySet()) {
							if ("name".equals(actionName)) {
								comp.setAction(
										(String) ((AXmlAttribute<?>) child.getAttributes().get(actionName)).getValue());
							}
						}
					}
				}
			}
		}
	}

	public void parseApkForComponents(String apkPath) {
		String fileName = apkPath.substring(apkPath.lastIndexOf('/') + 1, apkPath.lastIndexOf(".apk"));
		try {
			ProcessManifest processManifest = new ProcessManifest(apkPath);
			for (AXmlNode nd : processManifest.getActivities()) {
				ComponentBean comp = new ComponentBean(fileName, Globals.PACKAGE_NAME, COMPONENT_TYPE.ACTIVITY);
				parseComponent(nd, comp);
				if (comp.getName() != null) {
					this.compMap.put(comp.getName(), comp);
				}
			}
			for (AXmlNode nd : processManifest.getServices()) {
				ComponentBean comp = new ComponentBean(fileName, Globals.PACKAGE_NAME, COMPONENT_TYPE.SERVICE);
				parseComponent(nd, comp);
				if (comp.getName() != null) {
					this.compMap.put(comp.getName(), comp);
				}
			}
			for (AXmlNode nd : processManifest.getReceivers()) {
				ComponentBean comp = new ComponentBean(fileName, Globals.PACKAGE_NAME, COMPONENT_TYPE.RECEIVER);
				parseComponent(nd, comp);
				if (comp.getName() != null) {
					this.compMap.put(comp.getName(), comp);
				}
			}
			for (AXmlNode nd : processManifest.getProviders()) {
				ComponentBean comp = new ComponentBean(fileName, Globals.PACKAGE_NAME, COMPONENT_TYPE.PROVIDER);
				parseComponent(nd, comp);
				if (comp.getName() != null) {
					this.compMap.put(comp.getName(), comp);
				}
			}
			processManifest.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isAppDeveloperClass(String clazzName) {
		/*
		 * return true if the package name has ever appeared in manifest
		 */
		for (String item : compMap.keySet()) {
			if (Utils.commonDotsInPrefix(clazzName, item) >= 2) {
				return true;
			}
		}

		/*
		 * return true if the package name is obfuscated
		 */
		Pattern r = Pattern.compile("^[a-zA-Z]{1}\\.[a-zA-Z]{1}\\.");
		Matcher m = r.matcher(clazzName);
		if (m.find()) {
			return true;
		}

		return false;
	}

	public class ComponentBean {
		private String app;

		private String pkg;

		private ComponentManager.COMPONENT_TYPE type;

		private String name;

		private Boolean exported;

		private String permission;

		private Set<String> actions;

		public ComponentBean(String app, String pkg, ComponentManager.COMPONENT_TYPE type) {
			this.app = app;
			this.pkg = pkg;
			this.type = type;
			this.exported = null;
			this.actions = new HashSet<>();
		}

		public String getApp() {
			return this.app;
		}

		public void setApp(String app) {
			this.app = app;
		}

		public String getPkg() {
			return this.pkg;
		}

		public void setPkg(String pkg) {
			this.pkg = pkg;
		}

		public ComponentManager.COMPONENT_TYPE getType() {
			return this.type;
		}

		public void setType(ComponentManager.COMPONENT_TYPE type) {
			this.type = type;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isExported() {
			return this.exported.booleanValue();
		}

		public void setExported(boolean exported) {
			this.exported = Boolean.valueOf(exported);
		}

		public String getPermission() {
			return this.permission;
		}

		public void setPermission(String permission) {
			this.permission = permission;
		}

		public Set<String> getActions() {
			return this.actions;
		}

		public void setAction(String action) {
			this.actions.add(action);
		}
	}
}