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
package org.eclipse.n4js.tests.parser;

import org.eclipse.n4js.n4JS.Script;
import org.junit.Test;

/**
 * Parser tests for N4 specific types. Test methods with suffix "example" are taken from the N4JS spec.
 */
public class N4_04_13_2_MethodDeclarationsTest extends AbstractParserTest {

	@Test
	public void testMethodDeclarations() throws Exception {
		Script script = parseHelper.parse("""
				public class A {

					f1(): void {}

					abstract f1(): void
					abstract f2(): void;

					@Internal
					public abstract f3(): void

					protected f4(): any { return null; }

					public f5(): any { return null; }

					@Internal
					public <T> f6(): T { return null; }

					@Internal
					public f7(p1: any, p2: any): any { return p1; }
					private <T> f8(p1: T, p2: T) : void{ }

					@Internal
					public f9(p1: any, p2: any?): void {}
					@Internal
					public f10(p1: any, p2: any): void {}
					@Internal
					public f11(p1: any?, p2: any?): void {}

					static s1(): void {}
					static s2(): any { return null; }
					@Internal
					public static <T> s1(): T { return null; }
				}
				""");

		assertTrue(script.eResource().getErrors().toString(), script.eResource().getErrors().isEmpty());
	}

}
