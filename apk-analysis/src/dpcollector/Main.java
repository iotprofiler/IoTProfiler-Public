package dpcollector;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import analysisutils.AnalysisAPIs;
import analysisutils.Globals;
import analysisutils.Log;
import dpcollector.manager.AppMetaImporter;
import dpcollector.manager.ComponentManager;
import dpcollector.manager.DataBlockManager;
import dpcollector.manager.DataBlockManager.DataBlock;
import dpcollector.manager.MyResourceManager;
import dpcollector.manager.FieldManager;
import dpcollector.manager.FieldManager.FieldBean;
import dpcollector.transformer.DataBlockCollector;
import dpcollector.transformer.FieldAliasCollector;
import dpcollector.transformer.FieldGetterSetterCollector;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.Transform;
import soot.Value;
import soot.ValueBox;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

public class Main {

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Provide Params <APK_PATH> <TIMEOUT_SECONDS>");
			System.exit(-1);
		}

		Globals.setupApkForAnalysis(args[0]);
		int timeoutSeconds = Integer.parseInt(args[1]);

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss aaa z");
		Log.dumpln(Globals.LOG_FILE,
				String.format("Analysis of %s starts at %s", Globals.APK_PATH, sdf.format(cal.getTime())));

		ExecutorService executor = Executors.newFixedThreadPool(16);
		executor.execute(new Runnable() {
			@Override
			public void run() {
				new Main().run();
			}
		});
		executor.shutdown();

		try {
			if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			Log.dumpln(Globals.LOG_FILE, sw.toString());
		}

		FieldManager.getInstance().dumpJSONLineByLine();
		DataBlockManager.getInstance().dumpJSONLineByLine();

		Log.dumpln(Globals.LOG_FILE, "=======================================================");
		Log.dumpln(Globals.LOG_FILE, " #FINISH#");
		Log.dumpln(Globals.LOG_FILE, "=======================================================");
	}

	private void run() {
		ComponentManager.getInstance().parseApkForComponents(Globals.APK_PATH);
		AppMetaImporter.getInstance().importMetadata();

		AnalysisAPIs.runCustomPack("jtp", "jtp.datablockcollector",
				new Transform[] { new Transform("jtp.datablockcollector", new DataBlockCollector()) });
		this.collectFields();
		// need to run transformers in the following order because of dependency
		AnalysisAPIs.runCustomPack("jtp", "jtp.fieldgettersettercollector",
				new Transform[] { new Transform("jtp.fieldgettersettercollector", new FieldGetterSetterCollector()) });
		AnalysisAPIs.runCustomPack("jtp", "jtp.fieldaliascollector",
				new Transform[] { new Transform("jtp.fieldaliascollector", new FieldAliasCollector()) });
		
		FieldManager.getInstance().postProcessing();
		DataBlockManager.getInstance().postProcessing();
		
		this.checkDataLeaks();
	}

	private void collectFields() {
		for (SootClass fieldHostClazz : Scene.v().getClasses()) {
			// jump over if the class is of different package with the app
			if (fieldHostClazz.getShortName().startsWith("R$")
					|| !ComponentManager.getInstance().isAppDeveloperClass(fieldHostClazz.getName())) {
				continue;
			}

			for (SootField field : fieldHostClazz.getFields()) {
				// ignore final or static fields as they are less likely to represent a variable
				// device data point
				if (field.isStatic() || field.isFinal()) {
					continue;
				}

				// ignore "this"
				if (field.getName().indexOf("this") != -1
						|| !Constants.INTERESTING_TYPES.contains(field.getType().toString())
						|| !Pattern.compile(".*[a-z].*").matcher(field.getName()).matches()) {
					continue;
				}

				FieldManager.getInstance().addField(field);
			}
		}
	}

	private void checkDataLeaks() {
		Map<String, Object> srcFieldSigs = new HashMap<String, Object>();
		Map<String, Object> srcFieldGetterSigs = new HashMap<String, Object>();

		// add fields as source
		for (FieldBean bean : FieldManager.getInstance().getFieldMapping().values()) {
			if (!bean.isIotML()) {
				continue;
			}

			srcFieldSigs.put(bean.getFieldSig(), bean);
			for (String fieldGetterSig : bean.getGetterSigs()) {
				srcFieldGetterSigs.put(fieldGetterSig, bean);
			}
		}

		// add URLs as source
		Map<String, Object> stringSources = new HashMap<String, Object>();
		for (String url : MyResourceManager.getInstance().getURLs()) {
			stringSources.put(url, "URL");
		}

		MultiMap<String, Object> stmtSources = new HashMultiMap<String, Object>();
		// add DataBlocks as source
		for (DataBlock db : DataBlockManager.getInstance().getDataBlockMapping().values()) {
			if (!db.isIotML) {
				continue;
			}

			for (String stmt : db.stmts) {
				stmtSources.put(stmt, db);
			}
		}

		if (srcFieldSigs.size() < 1 && srcFieldGetterSigs.size() < 1 && stmtSources.size() < 1) {
			Log.dumpln(Globals.LOG_FILE, "NO iot data sources in checkDataModelLeaks, existing ...");
			return;
		} else {
			Log.dumpln(Globals.LOG_FILE, "checkDataModelLeaks: # of field signatures " + srcFieldSigs.size());
			Log.dumpln(Globals.LOG_FILE, "checkDataModelLeaks: # of field getters " + srcFieldGetterSigs.size());
			Log.dumpln(Globals.LOG_FILE, "checkDataModelLeaks: # of db stmts " + stmtSources.size());
		}

		AnalysisAPIs.taintPropagation(srcFieldGetterSigs.keySet(), AndroidSourceSinkSummary.SEND_APIS, null, null,
				stringSources.keySet(), srcFieldSigs.keySet(), stmtSources.keySet(), new ResultsAvailableHandler() {

					@SuppressWarnings("unchecked")
					@Override
					public void onResultsAvailable(IInfoflowCFG iif, InfoflowResults results) {
						if (results == null || results.getResults() == null) {
							Log.dumpln(Globals.LOG_FILE, "NO results found in checkDataModelLeaks");
						} else {
							for (ResultSinkInfo sink : results.getResults().keySet()) {
								Stmt skStmt = sink.getStmt();
								String skSig = null;
								SootClass skClz = null;
								if (skStmt.containsInvokeExpr()) {
									skSig = skStmt.getInvokeExpr().getMethod().getSignature();
									skClz = skStmt.getInvokeExpr().getMethod().getDeclaringClass();
								}
								if (skSig == null) {
									continue;
								}

								Set<String> sinkURLs = new HashSet<String>();
								for (ResultSourceInfo source : results.getResults().get(sink)) {
									Stmt srcStmt = source.getStmt();
									List<ValueBox> checkConstUseBoxes = srcStmt.getUseBoxes();
									for (ValueBox ccVB : checkConstUseBoxes) {
										if (ccVB.getValue() instanceof StringConstant) {
											String strV = ((StringConstant) ccVB.getValue()).value;
											if (stringSources.containsKey(strV)) {
												sinkURLs.add(strV);
												Log.dumpln(Globals.LOG_FILE, "Found url for leak: " + strV);
											}
										}
									}
								}

								for (ResultSourceInfo source : results.getResults().get(sink)) {
									Stmt srcStmt = source.getStmt();
									SootField field = null;

									if (srcStmt instanceof DefinitionStmt) {
										Value rhs = ((DefinitionStmt) srcStmt).getRightOp();
										if (rhs instanceof FieldRef) {
											SootField rhsField = ((FieldRef) rhs).getField();

											if (srcFieldSigs.containsKey(rhsField.getSignature())) {
												FieldBean fb = (FieldBean) (srcFieldSigs.get(rhsField.getSignature()));
												fb.addLeakAPI(skSig);
												fb.addUrls(sinkURLs);
												Log.dumpln(Globals.LOG_FILE, "Found field leak: " + fb.getFieldSig());
											}
										}
									}

									if (srcStmt.containsInvokeExpr()) {
										String srcSig = srcStmt.getInvokeExpr().getMethod().getSignature();
										if (srcFieldGetterSigs.containsKey(srcSig)) {
											FieldBean fb = (FieldBean) (srcFieldGetterSigs.get(srcSig));
											fb.addLeakAPI(skSig);
											fb.addUrls(sinkURLs);
											Log.dumpln(Globals.LOG_FILE, "Found field leak: " + fb.getFieldSig());
										}
									}

									if (stmtSources.containsKey(srcStmt.toString())) {
										String srcSig = srcStmt.toString();
										for (Object dbo : stmtSources.get(srcSig)) {
											DataBlock db = (DataBlock) dbo;
											db.addLeakAPI(skSig);
											db.addUrls(sinkURLs);
											Log.dumpln(Globals.LOG_FILE, "Found datablock leak: " + db.toString());
										}
									}
								}
							}
						}
					}
				});
	}

}