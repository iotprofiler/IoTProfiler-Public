package analysisutils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import soot.G;
import soot.PackManager;
import soot.Transform;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.ImplicitFlowMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.options.Options;

public class AnalysisAPIs {
	public static void setupDefaultEntries() {
		DefaultEntryPointCollector defaultEntryPointCollector = new DefaultEntryPointCollector();
		runCustomPack("jtp", "jtp.defaultEntryPointCollector", new Transform[] {new Transform("jtp.defaultEntryPointCollector", defaultEntryPointCollector)});
		Globals.ENTRY_POINTS = defaultEntryPointCollector.getAppUncalledMethods();
	}

	public static void runCustomPack(String packName, String transformSignature, Transform[] transforms) {
		long startTime = 0;
		long endTime = 0;
		startTime = System.currentTimeMillis();
		
		G.reset();
		for (Transform transform : transforms) {
			PackManager.v().getPack(packName).add(transform);
		}
		Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		Options.v().set_process_dir(Collections.singletonList(Globals.APK_PATH));
		Options.v().set_android_jars(Globals.FRAMEWORK_DIR);
		Options.v().set_whole_program(true);
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_force_overwrite(true);
		Options.v().set_process_multiple_dex(true);
		Options.v().set_exclude(Globals.EXCLUDED);
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_output_dir(Globals.JIMPLE_SUBDIR);
		
		soot.Main.main(new String[] {"-output-format", "J"});
		
		endTime = System.currentTimeMillis();
		System.out.println(String.format("Transform %s costs %d seconds", transformSignature, (endTime - startTime) / 1000));
	}

	public static InfoflowResults taintPropagation(Collection<String> srcAPIs, Collection<String> dstAPIs,
			TaintPropagationHandler taintPropHandler, TaintPropagationHandler backwardPropHandler,
			Set<String> stringSourcesSigs, Set<String> fieldSourceSigs, Set<String> stmtSourceSigs, ResultsAvailableHandler handler) {
		InfoflowResults results = null;
		
		long startTime = 0;
		long endTime = 0;
		startTime = System.currentTimeMillis();
		
		try {
			FileWriter fileWriter = new FileWriter(Globals.SRC_SINK_FILE);
			PrintWriter printWriter = new PrintWriter(fileWriter);
			for (String sc : srcAPIs) {
				printWriter.printf("%s -> _SOURCE_\n", sc);
			}
			for (String sk : dstAPIs) {
				printWriter.printf("%s -> _SINK_\n", sk);
			}
			printWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			G.reset();
			InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
			config.getAnalysisFileConfig().setTargetAPKFile(Globals.APK_PATH);
			config.getAnalysisFileConfig().setAndroidPlatformDir(Globals.FRAMEWORK_DIR);
			config.getAnalysisFileConfig().setSourceSinkFile(Globals.SRC_SINK_FILE);
			config.setImplicitFlowMode(ImplicitFlowMode.AllImplicitFlows);
			
			SetupApplication analyzer = new CustomSetupApplication(config, stringSourcesSigs, fieldSourceSigs,
					stmtSourceSigs);

			if (taintPropHandler != null) {
				analyzer.setTaintPropagationHandler(taintPropHandler);
			}

			if (backwardPropHandler != null) {
				analyzer.setBackwardsPropagationHandler(backwardPropHandler);
			}

			SootConfigForAndroid sootConf = new SootConfigForAndroid() {
				@Override
				public void setSootOptions(Options options, InfoflowConfiguration config) {
					super.setSootOptions(options, config);
					options.set_process_multiple_dex(true);
					options.set_exclude(Globals.EXCLUDED);
					options.set_no_bodies_for_excluded(true);
					options.set_output_format(Options.output_format_jimple);
					options.set_force_overwrite(true);
					options.set_output_dir(Globals.JIMPLE_SUBDIR);
				}
			};

			analyzer.setSootConfig(sootConf);
			EasyTaintWrapper easyTaintWrapper = new EasyTaintWrapper(
					new File(Globals.CONFIG_DIR + "EasyTaintWrapperSource.txt"));
			analyzer.setTaintWrapper(easyTaintWrapper);
			if (handler != null) {
				analyzer.addResultsAvailableHandler(handler);
			}
			results = analyzer.runInfoflow();
		} catch (Exception e) {
			e.printStackTrace();
		}

		endTime = System.currentTimeMillis();
		System.out.println(String.format("Taint propagation costs %d seconds", (endTime - startTime) / 1000));
		return results;
	}
}
