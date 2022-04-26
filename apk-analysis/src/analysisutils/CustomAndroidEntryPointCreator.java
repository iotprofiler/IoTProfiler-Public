package analysisutils;

import java.util.Collection;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Jimple;
import soot.jimple.NopStmt;
import soot.jimple.infoflow.android.entryPointCreators.AndroidEntryPointCreator;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.toolkits.scalar.Pair;

public class CustomAndroidEntryPointCreator extends AndroidEntryPointCreator {

	public CustomAndroidEntryPointCreator(ProcessManifest manifest, Collection<SootClass> components) {
		super(manifest, components);
		this.createEmptyMainMethod();
	}

	@Override
	protected SootMethod createDummyMainInternal() {
		SootMethod ret = super.createDummyMainInternal();

		for (Pair<String, String> entry_item : Globals.ENTRY_POINTS) {
			try {
				SootClass c = Scene.v().forceResolve(entry_item.getO1(), SootClass.BODIES);
				SootMethod m = c.getMethod(entry_item.getO2());
				Local localVal = generateClassConstructor(c, ret.getActiveBody());
				if (localVal == null)
					continue;
				localVarsForClasses.put(c, localVal);

				// Conditionally call the onCreate method
				NopStmt thenStmt = Jimple.v().newNopStmt();
				createIfStmt(thenStmt);
				searchAndBuildMethod(m.getSubSignature(), c, localVal);
				body.getUnits().add(thenStmt);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return ret;
	}

}
