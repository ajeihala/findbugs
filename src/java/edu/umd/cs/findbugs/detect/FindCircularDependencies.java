/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2005 Dave Brosius <dbrosius@users.sourceforge.net>
 * Copyright (C) 2005 University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package edu.umd.cs.findbugs.detect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.JavaClass;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.visitclass.Constants2;


public class FindCircularDependencies extends BytecodeScanningDetector implements Constants2
{
	private static HashMap<String, Set<String>> dependencyGraph = null;

	private BugReporter bugReporter;
	private String clsName;
	
	public FindCircularDependencies(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	public void setAnalysisContext(AnalysisContext analysisContext){
		dependencyGraph = new HashMap<String,Set<String>>();
	}
	
	public void visit(JavaClass obj) {
		clsName = obj.getClassName();
	}
	
	public void sawOpcode(int seen) {
		if ((seen == INVOKESPECIAL)
		||  (seen == INVOKESTATIC)
		||  (seen == INVOKEVIRTUAL)) {
			String refClsName = getClassConstantOperand();
			refClsName = refClsName.replace('/', '.');
			if (refClsName.startsWith("java"))
				return;
			
			if (clsName.equals(refClsName))
				return;
				
			if (clsName.startsWith(refClsName) && (refClsName.indexOf("$") >= 0))
				return;
			
			if (refClsName.startsWith(refClsName) && (clsName.indexOf("$") >= 0))
				return;
		
			Set<String> dependencies = dependencyGraph.get(clsName);
			if (dependencies == null) {
				dependencies = new HashSet<String>();
				dependencyGraph.put(clsName, dependencies);
			}
			
			dependencies.add(refClsName);
		}
	}
	
	public void report() {
		removeDependencyLeaves(dependencyGraph);
		
		LoopFinder lf = new LoopFinder();
		
		while (dependencyGraph.size() > 0) {
			String clsName = dependencyGraph.keySet().iterator().next();
			Set<String> loop = lf.findLoop(dependencyGraph, clsName);
			boolean pruneLeaves;
			if (loop != null) {
				BugInstance bug = new BugInstance(
						this,
						"CD_CIRCULAR_DEPENDENCY",
						NORMAL_PRIORITY);
				Iterator<String> lIt = loop.iterator();
				while (lIt.hasNext()) {
					String loopCls = lIt.next();
					bug.addClass(loopCls);
				}
				bugReporter.reportBug(bug);
				pruneLeaves = removeLoopLinks(dependencyGraph, loop);
			} else {
				dependencyGraph.remove(clsName);
				pruneLeaves = true;
			}
			if (pruneLeaves)
				removeDependencyLeaves(dependencyGraph);
		}
			
		dependencyGraph.clear();
	}
	
	private void removeDependencyLeaves(Map<String, Set<String>> dependencyGraph)
	{
		boolean changed = true;
		while (changed) {
			changed = false;
			Iterator<Map.Entry<String, Set<String>>> it = dependencyGraph.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, Set<String>> entry = it.next();
				String clsName = entry.getKey();
				Set<String> dependencies = entry.getValue();
				
				boolean foundClass = false;
				Iterator dit = dependencies.iterator();
				while (dit.hasNext()) {
					foundClass = dependencyGraph.containsKey(dit.next());
					if (!foundClass) {
						dit.remove();
						changed = true;
					}
				}
				if (dependencies.size() == 0) {
					it.remove();
					changed = true;
				}
			}
		}
	}
	
	private boolean removeLoopLinks(Map<String, Set<String>> dependencyGraph, Set<String> loop) 
	{
		Set<String> dependencies = null;
		Iterator<String> cIt = loop.iterator();
		while (cIt.hasNext()) {
			String clsName = cIt.next();
			if (dependencies != null)
				dependencies.remove(clsName);
			dependencies = dependencyGraph.get(clsName);
		}
		if (dependencies != null)
			dependencies.remove(loop.iterator().next());
		
		boolean removedClass = false;
		cIt = loop.iterator();
		while (cIt.hasNext()) {
			String clsName = cIt.next();
			dependencies = dependencyGraph.get(clsName);
			if (dependencies.size() == 0) {
				cIt.remove();
				removedClass = true;
			}
		}
		return removedClass;
	}
	
	static class LoopFinder
	{
		private Map<String, Set<String>> dGraph = null;
		private String startClass = null;
		private Set<String> visited = null;
		private Set<String> loop = null;
				
		public Set<String> findLoop(Map<String, Set<String>> dependencyGraph, String startCls) {
			dGraph = dependencyGraph;
			startClass = startCls;
			visited = new HashSet<String>();
			loop = new LinkedHashSet<String>();
			if (findLoop(startClass))
				return loop;
			return null;
		}
		
		private boolean findLoop(String curClass) {
			Set<String> dependencies = dGraph.get(curClass);
			if (dependencies == null)
				return false;
			
			visited.add(curClass);
			loop.add(curClass);
			Iterator<String> dit = dependencies.iterator();
			while (dit.hasNext()) {
				String depClass = dit.next();
				if (depClass.equals(startClass))
					return true;
				
				if (visited.contains(depClass))
					continue;
				
				if (findLoop(depClass))
					return true;
			}
			loop.remove(curClass);
			return false;			
		}
	}
}
