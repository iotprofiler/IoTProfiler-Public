package dpcollector.transformer;

import java.util.Iterator;
import java.util.Map;

import dpcollector.manager.FieldManager;
import soot.Body;
import soot.BodyTransformer;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;

public class FieldGetterSetterCollector extends BodyTransformer {
	protected void internalTransform(Body body, String arg1, Map<String, String> arg2) {
		SootMethod method = body.getMethod();
		SootClass clazz = method.getDeclaringClass();

		int totalInvokes = 0;
		Iterator<Unit> stmtIter = body.getUnits().iterator();
		while (stmtIter.hasNext()) {
			Stmt stmt = (Stmt) stmtIter.next();
			if (stmt.containsInvokeExpr()) {
				totalInvokes++;
			}
		}
		
		// heuristic: if a method does not have (or only a few) invocations, it could be a getter/setter
		boolean couldBeGetterOrSetter = (totalInvokes < 3) 
				&& !method.getName().contains("<init>") 
				&& !method.getName().contains("<clinit>")
				&& !method.getName().contains("toString");
		if (couldBeGetterOrSetter) {
			stmtIter = body.getUnits().iterator();
			while (stmtIter.hasNext()) {
				Stmt stmt = (Stmt) stmtIter.next();
				
				if (stmt instanceof DefinitionStmt) {
					SootField field = null;
					Value lhs = ((DefinitionStmt) stmt).getLeftOp();
					if (lhs instanceof FieldRef) {
						field = ((FieldRef) lhs).getField();
						if (clazz.equals(field.getDeclaringClass())) {
							continue;
						}
						
						if (FieldManager.getInstance().getFieldMapping().containsKey(field.getSignature())) {
							for (Type tp : method.getParameterTypes()) {
								if (tp.equals(field.getType())) {
									// it could be a setter if a matching parameter type is found!
									FieldManager.getInstance().putFieldSetter(field.getSignature(), method);
									break;
								}
							}
						}
					}

					Value rhs = ((DefinitionStmt) stmt).getRightOp();
					if (rhs instanceof FieldRef) {
						field = ((FieldRef) rhs).getField();

						if (FieldManager.getInstance().getFieldMapping().containsKey(field.getSignature())
								&& clazz.equals(field.getDeclaringClass())
								&& method.getReturnType().equals(field.getType())) {
							FieldManager.getInstance().putFieldGetter(field.getSignature(), method);
						}
					}
				}
			}
		}
	}
}
