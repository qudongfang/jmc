/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The contents of this file are subject to the terms of either the Universal Permissive License
 * v 1.0 as shown at http://oss.oracle.com/licenses/upl
 *
 * or the following license:
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjdk.jmc.agent.jfr.impl;

import java.security.ProtectionDomain;
import java.util.logging.Level;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.openjdk.jmc.agent.Agent;
import org.openjdk.jmc.agent.jfr.JFRTransformDescriptor;
import org.openjdk.jmc.agent.util.TypeUtils;

public class JFRClassVisitor extends ClassVisitor implements Opcodes {
	private final JFRTransformDescriptor transformDescriptor;
	private final ClassLoader definingClassLoader;
	private final ProtectionDomain protectionDomain;

	public JFRClassVisitor(ClassWriter cv, JFRTransformDescriptor descriptor, ClassLoader definingLoader,
			ProtectionDomain protectionDomain) {
		super(Opcodes.ASM5, cv);
		this.transformDescriptor = descriptor;
		this.definingClassLoader = definingLoader;
		this.protectionDomain = protectionDomain;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
		if (name.equals(transformDescriptor.getMethod().getName())
				&& desc.equals(transformDescriptor.getMethod().getSignature())) {
			return new JFRMethodAdvisor(transformDescriptor, Opcodes.ASM5, mv, access, name, desc);
		}
		return mv;
	}

	@Override
	public void visitEnd() {
		try {
			Class<?> c = generateEventClass();
			Agent.getLogger().log(Level.FINE, "Generated " + c);
		} catch (Throwable t) {
			Agent.getLogger().log(Level.SEVERE, "Failed to generate event class for " + transformDescriptor.toString(), //$NON-NLS-1$
					t);
		}
		super.visitEnd();
	}

	private Class<?> generateEventClass() throws Exception {
		byte[] eventClass = JFREventClassGenerator.generateEventClass(transformDescriptor);
		return TypeUtils.getUnsafe().defineClass(transformDescriptor.getEventClassName(), eventClass, 0,
				eventClass.length, definingClassLoader, protectionDomain);
	}

}
