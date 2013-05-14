package org.kframework.kompile.lint;

import org.kframework.kil.*;
import org.kframework.kil.loader.DefinitionHelper;

/**
 * Inifite rewrite rule is a lint rule that looks for rules that can lead to infinite rewrite.
 */
public class InfiniteRewrite extends KlintRule{

	public InfiniteRewrite(Definition javaDef, DefinitionHelper definitionHelper) {
		super(definitionHelper);
		this.javaDef = javaDef;
	}
	@Override
	public void run() {
		for(Rewrite rewrite : getRewrites(true)){
			Term left = rewrite.getLeft();
			Term right = rewrite.getRight();

			/* If is a cell, goes deep in it looking for a termCons */
			if(left instanceof Cell){
				Cell cell = (Cell)left;
				while(cell.getContents() instanceof Cell){
					cell = (Cell)cell.getContents();
				}
				left = cell.getContents();
			}
			if(right instanceof Cell){
				Cell cell = (Cell)right;
				while(cell.getContents() instanceof Cell){
					cell = (Cell)cell.getContents();
				}
				right = cell.getContents();
			}

			/* If is a termcons, check it agains the productions */
			if(left instanceof TermCons && right instanceof TermCons){
				TermCons leftTermCons = (TermCons)left;
				TermCons rightTermCons = (TermCons)right;
				checkProductions(leftTermCons,rightTermCons,rewrite);
			}
		}

	}

	private void checkProductions(TermCons leftTermCons, TermCons rightTermCons, Rewrite rewrite) {
		Production leftTermProd = definitionHelper.conses.get(leftTermCons.getCons());
		Production rightTermProd = definitionHelper.conses.get(rightTermCons.getCons());

		if(leftTermProd.equals(rightTermProd)){
			warning("Possible infinite rewrite: ", rewrite);
		}else{
			for(Term term : rightTermCons.getContents()){
				if(term instanceof TermCons){
					checkProductions(leftTermCons, (TermCons)term, rewrite);
				}
			}
		}
	}
}
