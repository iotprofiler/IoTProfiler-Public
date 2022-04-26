package analysisutils;

import java.util.*;
import soot.*;
import soot.toolkits.scalar.*;
import soot.toolkits.graph.*;
import soot.options.*;

public class ReachingDefinition implements LocalDefs {
	Map<LocalUnitPair, List> localUnitPairToDefs;
	
	public ReachingDefinition(UnitGraph g) {
		if (Options.v().time())
			Timers.v().defsTimer.start();

		ReachingDefinitionAnalysis analysis = new ReachingDefinitionAnalysis(g);

		if (Options.v().time())
			Timers.v().defsPostTimer.start();

		// Build localUnitPairToDefs map
		{
			Iterator unitIt = g.iterator();

			localUnitPairToDefs = new HashMap<LocalUnitPair, List>(g.size() * 2 + 1, 0.7f);

			while (unitIt.hasNext()) {
				Unit s = (Unit) unitIt.next();

				Iterator boxIt = s.getUseBoxes().iterator();

				while (boxIt.hasNext()) {
					ValueBox box = (ValueBox) boxIt.next();

					if (box.getValue() instanceof Local) {
						Local l = (Local) box.getValue();
						LocalUnitPair pair = new LocalUnitPair(l, s);

						if (!localUnitPairToDefs.containsKey(pair)) {
							IntPair intPair = analysis.localToIntPair.get(l);

							ArrayPackedSet value = (ArrayPackedSet) analysis.getFlowBefore(s);

							List unitLocalDefs = value.toList(intPair.op1, intPair.op2);

							localUnitPairToDefs.put(pair, Collections.unmodifiableList(unitLocalDefs));
						}
					}
				}
			}
		}

		if (Options.v().time())
			Timers.v().defsPostTimer.end();

		if (Options.v().time())
			Timers.v().defsTimer.end();
	}

	public boolean hasDefsAt(Local l, Unit s) {
		return localUnitPairToDefs.containsKey(new LocalUnitPair(l, s));
	}

	public List<Unit> getDefsOfAt(Local l, Unit s) {
		LocalUnitPair pair = new LocalUnitPair(l, s);

		List<Unit> toReturn = localUnitPairToDefs.get(pair);

		if (toReturn == null)
			throw new RuntimeException("Illegal LocalDefs query; local " + l + " has no definition at " + s.toString());

		return toReturn;
	}
	
	public List<Unit> getUsesofDef(Local l, Unit s) {
		List<Unit> toReturn = new LinkedList<Unit>();
		
		for (Map.Entry<LocalUnitPair, List> entry: localUnitPairToDefs.entrySet()) {
			LocalUnitPair lup = entry.getKey();
			List<Unit> defs = entry.getValue();
			
			if (lup.getLocal() == l && defs.contains(s)) {
				toReturn.add(lup.getUnit());
			}
		}
		
		return toReturn;
		
	}

	public List<Unit> getDefsOf(Local l) {
		List<Unit> list = new LinkedList<Unit>();

		for (Map.Entry<LocalUnitPair, List> entry : localUnitPairToDefs.entrySet()) {
			if (l == entry.getKey().getLocal()) {
				list.addAll(entry.getValue());
			}
		}

		return list;
	}
	
	
}
