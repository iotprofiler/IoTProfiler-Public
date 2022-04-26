package dpcollector.transformer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.AbstractMap.SimpleEntry;
import java.util.regex.Pattern;

import dpcollector.Constants;
import dpcollector.Utils;
import dpcollector.manager.ComponentManager;
import dpcollector.manager.DataBlockManager;
import dpcollector.manager.DataBlockManager.DataBlock;
import dpcollector.manager.MyResourceManager;
import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import analysisutils.ReachingDefinition;

public class DataBlockCollector extends BodyTransformer {
	private MultiMap<DataBlock, SimpleEntry<String, String>> dbStringToStmts;
	private Set<Stmt> trackedStmts;

	public Set<SimpleEntry<String, String>> getDbStrings(DataBlock db) {
		return dbStringToStmts.get(db);
	}

	public DataBlockCollector() {
		dbStringToStmts = new HashMultiMap<DataBlock, SimpleEntry<String, String>>();
	}

	private Set<String> backTrackConstants(Set<Stmt> stmts, Body body, ExceptionalUnitGraph eug,
			ReachingDefinition mrd) {
		Set<String> ret = new HashSet<String>();
		
		Stack<Stmt> usesStack = new Stack<Stmt>();
		for (Stmt stmt : stmts) {
			if (!this.trackedStmts.contains(stmt)) {
				usesStack.push(stmt);
			}
		}
		
		while (!usesStack.isEmpty()) {
			Stmt stmt = usesStack.pop();
			
			if (this.trackedStmts.contains(stmt)) {
				continue;
			}
			
			// add constant strings of stmt
			List<ValueBox> checkConstUseBoxes = stmt.getUseBoxes();
			for (ValueBox ccVB : checkConstUseBoxes) {
				if (ccVB.getValue() instanceof StringConstant) {
					ret.add(((StringConstant) ccVB.getValue()).value);
				}
			}
			
			// mark stmt as processed
			this.trackedStmts.add(stmt);
			
			// find definitions of Locals at stmt
			List<ValueBox> usesBoxes = stmt.getUseBoxes();
			Iterator usesIter = usesBoxes.iterator();
			while (usesIter.hasNext()) {
				ValueBox usesBox = (ValueBox) usesIter.next();
				if (usesBox.getValue() instanceof Local) {
					List<Unit> defs = mrd.getDefsOfAt((Local) usesBox.getValue(), stmt);
					for (Unit def : defs) {
						Stmt defStmt = (Stmt) def;
						if (!this.trackedStmts.contains(defStmt)) {
							usesStack.push(defStmt);
						}
						
						// extend to other uses of definition
						List<Unit> usesUnits = mrd.getUsesofDef((Local) usesBox.getValue(), defStmt);
						for (Unit useUnit : usesUnits) {
							if (!this.trackedStmts.contains((Stmt) useUnit)) {
								usesStack.push((Stmt) useUnit);
							}
						}
					}
				}
			}
		}
		
		return ret;
	}

	@Override
	protected void internalTransform(Body body, String string, Map map) {
		this.trackedStmts = new HashSet<Stmt>();

		SootMethod method = body.getMethod();
		SootClass clazz = method.getDeclaringClass();
		boolean isAppDeveloperClazz = ComponentManager.getInstance().isAppDeveloperClass(clazz.getName());

		ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
		ReachingDefinition mrd = new ReachingDefinition(eug);

		Iterator<Unit> iter = body.getUnits().iterator();
		while (iter.hasNext()) {
			Stmt s = (Stmt) iter.next();

			if (!s.containsInvokeExpr()) {
				continue;
			}

			SootMethod m = s.getInvokeExpr().getMethod();
			SootClass c = m.getDeclaringClass();
			if (m.isConcrete()) {
				MyResourceManager.getInstance().addCallerEntry(method.getSignature(), m.getSignature());
			}
		}

		if (isAppDeveloperClazz) {
			this.parseDataBlocks(body, method, clazz, eug, mrd);

			for (Unit u : body.getUnits()) {
				Stmt s = (Stmt) u;
				List<ValueBox> checkConstUseBoxes = s.getUseBoxes();
				for (ValueBox ccVB : checkConstUseBoxes) {
					if (ccVB.getValue() instanceof StringConstant) {
						String strV = ((StringConstant) ccVB.getValue()).value;
						if (Utils.isURL(strV)) {
							MyResourceManager.getInstance().addUrl(strV, clazz.getName(), method.getSignature());
						}
					}
				}
			}
		}
	}

	private void parseDataBlocks(Body body, SootMethod method, SootClass clazz, ExceptionalUnitGraph eug,
			ReachingDefinition mrd) {
		MultiMap<String, Stmt> clazz2Stmt = new HashMultiMap<String, Stmt>();

		for (Unit u : body.getUnits()) {
			Stmt s = (Stmt) u;
			if (!s.containsInvokeExpr()) {
				continue;
			}

			SootMethod m = s.getInvokeExpr().getMethod();
			SootClass c = m.getDeclaringClass();

			// for pre-defined datablock types, we collect it for sure.
			if (DataBlockManager.CATEGORIES.contains(c.getName())) {
				clazz2Stmt.put(c.getName(), s);
			}
		}

		for (String cname : clazz2Stmt.keySet()) {
			DataBlock block = (DataBlockManager.getInstance()).new DataBlock(method.getSignature(), clazz.getName(),
					cname);
			String datablockSig = method.getSignature() + cname;

			// we collect data block only for a few `CATEGORIES`
			if (!DataBlockManager.CATEGORIES.contains(cname)) {
				continue;
			}

			for (Stmt s : clazz2Stmt.get(cname)) {
				List<ValueBox> checkConstUseBoxes = s.getUseBoxes();
				for (ValueBox ccVB : checkConstUseBoxes) {
					if (ccVB.getValue() instanceof StringConstant) {
						String sv = ((StringConstant) ccVB.getValue()).value;
						if (sv.length() >= Constants.MIN_NONOBFUSCATED_LENGTH
								&& Pattern.compile(".*[a-z].*").matcher(sv).matches()) {
							block.addValue(sv);
							block.addStmt(s.toString());
							this.dbStringToStmts.put(block, new SimpleEntry<String, String>(sv, s.toString()));
						}
					}
				}
			}
			
			ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
			executor.setRemoveOnCancelPolicy(true);			
			executor.execute(new Runnable() {
				@Override
				public void run() {
					// System.out.println("Start tracking " + method.getSignature() + "--" + cname);
					Set<String> strs = DataBlockCollector.this.backTrackConstants(clazz2Stmt.get(cname), body, eug,
							mrd);
					for (String s : strs) {
						if (s.length() >= Constants.MIN_NONOBFUSCATED_LENGTH
								&& Pattern.compile(".*[a-z].*").matcher(s).matches()) {
							block.addValue(s);
						}
					}
					// System.out.println("End tracking " + method.getSignature() + "--" + cname);
				}
			});
			
			try {
				executor.shutdown();
				if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
			}

			if (block.values.size() >= Constants.MIN_DATABLOCK_VALUES) {
				DataBlockManager.getInstance().getDataBlockMapping().put(datablockSig, block);
			}
		}
	}

}
