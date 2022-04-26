package dpcollector.transformer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import analysisutils.ReachingDefinition;
import dpcollector.Constants;
import dpcollector.manager.FieldManager;
import dpcollector.manager.FieldManager.FieldBean;
import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.CastExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.Pair;

/* 
 * Find alias for a fields
 * 1) if the field is obfuscated, try find the getters and setters (in some cases, fields are obfuscated, but method names are not)
 * 2) find whether a field is concatenated to a StringBuilder
 * 3) find if a field is put into another dictionary; if so, the dictionary key is an alias.
 */
public class FieldAliasCollector extends BodyTransformer {

	@Override	
	protected void internalTransform(Body body, String arg1, Map<String, String> arg2) {
		ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
		ReachingDefinition mrd = new ReachingDefinition(eug);
		
		// Check StringBuilder
		Stmt lastSbInvokeStmt = null;
		Iterator<Unit> stmtIter = body.getUnits().iterator();
		while (stmtIter.hasNext()) {
			Stmt stmt = (Stmt) stmtIter.next();
			if (!stmt.containsInvokeExpr()) {
				continue;
			}
			
			SootMethod calleeMethod = stmt.getInvokeExpr().getMethod();
			// if it's a stringbuilder.append(field), then find the related constant for the field.
			if (calleeMethod.getDeclaringClass().getName().equals("java.lang.StringBuilder") && 
					calleeMethod.getName().equals("append")) {
				Value arg0 = stmt.getInvokeExpr().getArg(0);
				if (arg0 instanceof Local) {
					List<Unit> defs = mrd.getDefsOfAt((Local) arg0, stmt);
					for (Unit def : defs) {
						SootField interestField = null;
						if (def instanceof DefinitionStmt) {
							Value rhs = ((DefinitionStmt) def).getRightOp();
							
							// if its a field access
							if (rhs instanceof FieldRef) {
								SootField rhsField = ((FieldRef) rhs).getField();
								if (FieldManager.getInstance().getFieldMapping().containsKey(rhsField.getSignature())) {
									interestField = rhsField;
								}
							}

							// if its a getter invocation
							if (interestField == null) {
								if (rhs instanceof InvokeExpr) {
									InvokeExpr ivk = (InvokeExpr) rhs;
									FieldBean rhsFieldBean = FieldManager.getInstance()
											.getFieldByGetterSig(ivk.getMethod().getSignature());
									if (rhsFieldBean != null) {
										interestField = rhsFieldBean.getField();
									}
								}
							}
						}
						
						// parameters of the current stringbuilder.append invocation include field access, then add the constant string of the last stringbuilder.append
						if (interestField != null && lastSbInvokeStmt != null) {
							Iterator itUse = lastSbInvokeStmt.getUseBoxes().iterator();
							while (itUse.hasNext()) {
								ValueBox vbox = (ValueBox) itUse.next();
								if (vbox.getValue() instanceof StringConstant) {
									String constStr = ((StringConstant) vbox.getValue()).value;
									String[] extArray = constStr.split("\\W+");
									if (extArray.length == 1) {
										FieldBean fb = FieldManager.getInstance().getFieldMapping().get(interestField.getSignature());
										fb.addExtra(extArray[0]);
									}
								}
							}
						}
					}
				}
				lastSbInvokeStmt = stmt;
			}
		}
		
		// other invocations like dict.put('fieldNameAliasA', clazzA.fieldA)
		stmtIter = body.getUnits().iterator();
		while (stmtIter.hasNext()) {
			Stmt stmt = (Stmt) stmtIter.next();
			if (!stmt.containsInvokeExpr()) {
				continue;
			}
			
			List<Value> args = stmt.getInvokeExpr().getArgs();
			List<String> constants = new ArrayList<String>();
			for (Value arg : args) {
				if (arg instanceof StringConstant) {
					String constStr = ((StringConstant) arg).value;
					String[] extArray = constStr.split("\\W+");
					if (extArray.length == 1) {
						boolean atleastOneAlpha = extArray[0].matches(".*[a-zA-Z]+.*");
						if (extArray[0].length() >= Constants.MIN_NONOBFUSCATED_LENGTH &&
								atleastOneAlpha) {
							constants.add(extArray[0]);
						}
					}
				}
			}
			
			if (constants.size() < 1) {
				continue;
			}
			
			int step = 0;
			List<Unit> allDefs = new ArrayList<Unit>();
			
			Set<Pair<Stmt, Value>> unHandledValues = new HashSet<Pair<Stmt, Value>>();
			for (Value arg : args) {
				if (arg instanceof Local) {
					unHandledValues.add(new Pair<Stmt, Value>(stmt, arg));
				}
			}
			Set<Pair<Stmt, Value>> newUnHandledValues = new HashSet<Pair<Stmt, Value>>();
			while (step < Constants.BACKWARD_STEPS) {
				for (Pair<Stmt, Value> pr : unHandledValues) {
					List<Unit> defs = mrd.getDefsOfAt((Local) pr.getO2(), pr.getO1());
					for (Unit def : defs) {
						if (def instanceof DefinitionStmt) {
							allDefs.add(def);
							
							if (((DefinitionStmt) def).containsInvokeExpr()) {
								InvokeExpr invokeExpr = ((DefinitionStmt) def).getInvokeExpr();
								if (!invokeExpr.getMethod().getDeclaringClass().isApplicationClass()) {
									for (Value newArg : invokeExpr.getArgs()) {
										if (!(newArg instanceof Local)) {
											continue;
										}
										newUnHandledValues.add(new Pair<Stmt, Value>((Stmt) def, newArg));
									}	
								}
							} else if (((DefinitionStmt) def).getRightOp() instanceof CastExpr) {
								CastExpr castExpr = (CastExpr)(((DefinitionStmt) def).getRightOp());
								if (castExpr.getOpBox().getValue() instanceof Local) {
									newUnHandledValues.add(new Pair<Stmt, Value>((Stmt) def, castExpr.getOpBox().getValue()));
								}
							}
						}
					}
				}
				
				unHandledValues.clear();
				unHandledValues.addAll(newUnHandledValues);
				newUnHandledValues.clear();
				step += 1;
			}
			
			for (Unit def : allDefs) {
				SootField interestField = null;
				
				Value rhs = ((DefinitionStmt) def).getRightOp();
				// if its a field access
				if (rhs instanceof FieldRef) {
					SootField rhsField = ((FieldRef) rhs).getField();
					if (FieldManager.getInstance().getFieldMapping().containsKey(rhsField.getSignature())) {
						interestField = rhsField;
					}
				}

				// if its a getter invocation
				if (interestField == null) {
					if (rhs instanceof InvokeExpr) {
						InvokeExpr ivk = (InvokeExpr) rhs;
						FieldBean rhsFieldBean = FieldManager.getInstance()
								.getFieldByGetterSig(ivk.getMethod().getSignature());
						if (rhsFieldBean != null) {
							interestField = rhsFieldBean.getField();
						}
					}
				}
				
				if (interestField != null) {
					FieldBean fb = FieldManager.getInstance().getFieldMapping().get(interestField.getSignature());
					fb.addExtra(constants.get(constants.size()-1));
					break;
				}
			}
		}
	}
}
