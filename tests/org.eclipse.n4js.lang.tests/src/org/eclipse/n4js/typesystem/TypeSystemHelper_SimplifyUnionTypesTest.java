/**
 * Copyright (c) 2016 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.typesystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.n4js.N4JSInjectorProviderWithIssueSuppression;
import org.eclipse.n4js.ts.typeRefs.TypeRef;
import org.eclipse.n4js.ts.typeRefs.UnionTypeExpression;
import org.eclipse.n4js.types.utils.TypeUtils;
import org.eclipse.n4js.typesystem.utils.RuleEnvironment;
import org.eclipse.n4js.validation.IssueCodes;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/*
 * Tests for {@link TypeSystemHelper#join(RuleEnvironment, TypeRef...)} method with union types.
 */
@RunWith(XtextRunner.class)
@InjectWith(N4JSInjectorProviderWithIssueSuppression.class)
public class TypeSystemHelper_SimplifyUnionTypesTest extends AbstractTypeSystemHelperTests {

	@Before
	public void prepareTypeDefs() {
		setDefaultTypeDefinitions();
	}

	/**
	 * Asserts that join of given type expressions equals a given expected type, for comparison
	 * {@link TypeRef#getTypeRefAsString()} is used.
	 */
	void assertSimplify(String expectedType, String typeExpressionsToBeSimplified) {
		assertSimplify(expectedType, typeExpressionsToBeSimplified, new String[0]);
	}

	void assertSimplify(String expectedType, String typeExpressionsToBeSimplified, String... expectedIssueMsg) {
		RuleEnvironment G = assembler.prepareScriptAndCreateRuleEnvironment(expectedIssueMsg,
				typeExpressionsToBeSimplified);
		TypeRef typeRef = assembler.getTypeRef(typeExpressionsToBeSimplified);
		assertTrue("Error in test setup, expected union type", typeRef instanceof UnionTypeExpression);
		TypeRef simplified = TypeUtils.copy(tsh.simplify(G, (UnionTypeExpression) typeRef));
		assertEquals(expectedType, simplified.getTypeRefAsString());
	}

	/*
	 * Test some assumptions.
	 */
	@Test
	public void testJoinAsumptions() {
		assertJoin("G<? extends A>", "G<A>", "G<B>");
		assertJoin("A", "A", "B");

		// G is instanceof of N4OBject ;-)
		assertJoin("N4Object", "G<A>", "A");
	}

	@Test
	public void testSimplifyDuplicates() {
		assertSimplify("A", "union{A}");
		assertSimplify("A", "union{A,B}", IssueCodes.UNI_REDUNDANT_SUBTYPE);
		assertSimplify("A", "union{A,B,A}", IssueCodes.UNI_REDUNDANT_SUBTYPE);
	}

	@Test
	public void testSimplifyNestedUnions() {
		assertSimplify("A", "union{A,B,union{A,B}}", IssueCodes.UNI_REDUNDANT_SUBTYPE,
				IssueCodes.UNI_REDUNDANT_SUBTYPE);
		assertSimplify("union{A,B,C}", "union{A,B,union{B,C}}", IssueCodes.UNI_REDUNDANT_SUBTYPE,
				IssueCodes.UNI_REDUNDANT_SUBTYPE, IssueCodes.UNI_REDUNDANT_SUBTYPE);
	}

	@Test
	public void testSimplifyUndefinedAndNull() {
		assertSimplify("A", "union{A,B,undefined}", IssueCodes.UNI_REDUNDANT_SUBTYPE);
		assertSimplify("A", "union{A,undefined,B}", IssueCodes.UNI_REDUNDANT_SUBTYPE);
		assertSimplify("A", "union{A,undefined}");
		assertSimplify("A", "union{undefined,A}");
		assertSimplify("undefined", "union{undefined,undefined}");
	}

	@Test
	public void testSimplifyObjectAndOther() {
		assertSimplify("Object", "union{Object,() => void}");
		assertSimplify("Object", "union{Object,Function}");
		assertSimplify("union{~Object,{function():void}}", "union{~Object,() => void}");
		assertSimplify("union{~Object,Function}", "union{~Object,Function}");
	}

	@Test
	public void testSimplifyStructObjectsAndOtherWithOptionalField() {
		assertSimplify("union{~Object,~Object with { optField?: int }}",
				"union{~Object,~Object with {optField?: int}}");
		assertSimplify("union{~Object,~Array<any>}", "union{~Object,~Array<any>}");
		assertSimplify("union{~Object,Array<any>}", "union{~Object,Array<any>}");
	}

	@Test
	public void testDontSimplifyNonDuplicates() {
		assertSimplify(
				"union{Array<union{string,number}>,Array<union{A,D}>}", // must not be simplified to "Array<union{A,D}>"
				"union{Array<union{string,number}>,Array<union{A,D}>}");
		assertSimplify(
				"union{type{A},type{D}}", // must not be simplified to "type{A}" (note: D is not a subtype of A)
				"union{type{A},type{D}}");
		assertSimplify(
				"union{constructor{? extends A},constructor{? extends D}}", // must not be simplified to "type{A}"
																			// (note: D is not a subtype of A)
				"union{constructor{? extends A},constructor{? extends D}}");
		assertSimplify(
				"union{Array<type{A}>,Array<type{D}>}", // must not be simplified to "Array<type{A}>"
				"union{Array<type{A}>,Array<type{D}>}");
		assertSimplify(
				"union{Array<constructor{? extends A}>,Array<constructor{? extends D}>}", // must not be simplified to
																							// "Array<type{A}>"
				"union{Array<constructor{? extends A}>,Array<constructor{? extends D}>}");
		// this was already working correctly before the bug fix:
		assertSimplify(
				"union{EnumA,EnumB}", // must not be simplified to "EnumA"
				"union{EnumA,EnumB}");
		assertSimplify(
				"union{Array<EnumA>,Array<EnumB>}", // must not be simplified to "Array<EnumA>"
				"union{Array<EnumA>,Array<EnumB>}");
	}
}
