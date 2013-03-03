/**
 * Copyright (C) 2010-2013 eBusiness Information, Excilys Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.processing;

import static com.sun.codemodel.JMod.PRIVATE;
import static com.sun.codemodel.JMod.PUBLIC;

import java.lang.annotation.Annotation;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.androidannotations.annotations.EView;
import org.androidannotations.helper.APTCodeModelHelper;
import org.androidannotations.processing.EBeansHolder.Classes;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;

public class EViewProcessor extends GeneratingElementProcessor {

	private static final String ALREADY_INFLATED_COMMENT = "" // +
			+ "The mAlreadyInflated_ hack is needed because of an Android bug\n" // +
			+ "which leads to infinite calls of onFinishInflate()\n" //
			+ "when inflating a layout with a parent and using\n" //
			+ "the <merge /> tag." //
	;

	private static final String SUPPRESS_WARNING_COMMENT = "" //
			+ "We use @SuppressWarning here because our java code\n" //
			+ "generator doesn't know that there is no need\n" //
			+ "to import OnXXXListeners from View as we already\n" //
			+ "are in a View." //
	;

	private final APTCodeModelHelper codeModelHelper;

	public EViewProcessor() {
		codeModelHelper = new APTCodeModelHelper();
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return EView.class;
	}

	@Override
	public int getGeneratedClassModifiers(Element element) {
		if (element.getModifiers().contains(Modifier.ABSTRACT)) {
			return JMod.PUBLIC | JMod.ABSTRACT;
		} else {
			return JMod.PUBLIC | JMod.FINAL;
		}
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeansHolder eBeansHolder, EBeanHolder holder) throws Exception {

		Classes classes = eBeansHolder.classes();

		holder.generatedClass.annotate(SuppressWarnings.class).param("value", "unused");
		holder.generatedClass.javadoc().append(SUPPRESS_WARNING_COMMENT);

		{
			holder.contextRef = holder.generatedClass.field(PRIVATE, classes.CONTEXT, "context_");
		}

		JMethod init;
		{
			// init
			init = holder.generatedClass.method(PRIVATE, codeModel.VOID, "init_");
			holder.initBody = init.body();
			holder.wrapInitWithNotifier();
			holder.initBody.assign((JFieldVar) holder.contextRef, JExpr.invoke("getContext"));
		}

		JFieldVar mAlreadyInflated_ = holder.generatedClass.field(PRIVATE, JType.parse(codeModel, "boolean"), "mAlreadyInflated_", JExpr.FALSE);

		// onFinishInflate
		JMethod onFinishInflate = holder.generatedClass.method(PUBLIC, codeModel.VOID, "onFinishInflate");
		onFinishInflate.annotate(Override.class);
		onFinishInflate.javadoc().append(ALREADY_INFLATED_COMMENT);

		JBlock ifNotInflated = onFinishInflate.body()._if(JExpr.ref("mAlreadyInflated_").not())._then();
		ifNotInflated.assign(mAlreadyInflated_, JExpr.TRUE);

		holder.invokeViewChanged(ifNotInflated);

		// finally
		onFinishInflate.body().invoke(JExpr._super(), "onFinishInflate");

		codeModelHelper.copyConstructorsAndAddStaticEViewBuilders(element, codeModel, holder.generatedClass._extends(), holder, onFinishInflate, init);

		{
			// init if activity
			holder.initIfActivityBody = codeModelHelper.ifContextInstanceOfActivity(holder, holder.initBody);
			holder.initActivityRef = codeModelHelper.castContextToActivity(holder, holder.initIfActivityBody);
		}

	}

}
