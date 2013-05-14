package org.kframework.compile;

import org.kframework.compile.transformers.ResolveContextAbstraction;
import org.kframework.compile.transformers.ResolveDefaultTerms;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.CompilerSteps;
import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.compile.utils.ConfigurationStructureVisitor;
import org.kframework.kil.Definition;
import org.kframework.kil.loader.DefinitionHelper;

/**
 * Initially created by: Traian Florin Serbanuta
 * <p/>
 * Date: 12/6/12
 * Time: 12:27 PM
 */
public class ResolveConfigurationAbstraction extends CompilerSteps<Definition> {

	public ResolveConfigurationAbstraction(ConfigurationStructureMap cfgStr, DefinitionHelper definitionHelper) {
		super(definitionHelper);
		this.cfgStr = cfgStr;
	}

	private ConfigurationStructureMap cfgStr;
	@Override
	public Definition compile(Definition def, String stepName) throws CompilerStepDone {
		ConfigurationStructureVisitor cfgStrVisitor = new
				ConfigurationStructureVisitor(cfgStr, definitionHelper);
		def.accept(cfgStrVisitor);
		int cfgMaxLevel = cfgStrVisitor.getMaxLevel();
		add(new ResolveContextAbstraction(cfgMaxLevel, cfgStr, definitionHelper));
		add(new ResolveDefaultTerms(cfgStr, definitionHelper));
		return super.compile(def, stepName);
	}
}
