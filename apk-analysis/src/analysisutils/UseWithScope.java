package analysisutils;

import soot.jimple.*;

public class UseWithScope {
	private Stmt use;
	private Stmt scopeEnd;

	public UseWithScope(Stmt use) {
		this.use = use;
		this.scopeEnd = null;
	}

	public UseWithScope(Stmt use, Stmt scope) {
		this.use = use;
		this.scopeEnd = scope;
	}

	public Stmt getUse() {
		return this.use;
	}

	public Stmt getScopeEnd() {
		return this.scopeEnd;
	}

	public void setUse(Stmt use) {
		this.use = use;
	}

	public void setScopeEnd(Stmt scope) {
		this.scopeEnd = scope;
	}

	@Override
	public boolean equals(Object o) {

		// If the object is compared with itself then return true
		if (o == this) {
			return true;
		}

		/*
		 * Check if o is an instance of Complex or not "null instanceof [type]" also
		 * returns false
		 */
		if (!(o instanceof UseWithScope)) {
			return false;
		}

		// typecast o to Complex so that we can compare data members
		UseWithScope c = (UseWithScope) o;

		// Compare the data members and return accordingly
		return c.getUse().toString().equals(this.getUse().toString())
				&& c.getScopeEnd().toString().equals(this.getScopeEnd().toString());
	}
}