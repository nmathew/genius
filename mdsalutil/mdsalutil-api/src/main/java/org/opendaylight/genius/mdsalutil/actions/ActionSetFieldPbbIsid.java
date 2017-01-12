/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.mdsalutil.actions;

import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.field._case.SetFieldBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.ProtocolMatchFieldsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.protocol.match.fields.PbbBuilder;

/**
 * Set PBB ISID field action.
 */
public class ActionSetFieldPbbIsid extends ActionInfo {
    private final long isid;

    public ActionSetFieldPbbIsid(long isid) {
        this(0, isid);
    }

    public ActionSetFieldPbbIsid(int actionKey, long isid) {
        super(ActionType.set_field_pbb_isid, new String[] {Long.toString(isid)}, actionKey);
        this.isid = isid;
    }

    @Deprecated
    public ActionSetFieldPbbIsid(ActionInfo actionInfo) {
        this(actionInfo.getActionKey(), Long.parseLong(actionInfo.getActionValues()[0]));
    }

    @Override
    public Action buildAction() {
        return buildAction(getActionKey());
    }

    public Action buildAction(int newActionKey) {
        return new ActionBuilder()
            .setAction(
                new SetFieldCaseBuilder()
                    .setSetField(
                        new SetFieldBuilder()
                            .setProtocolMatchFields(
                                new ProtocolMatchFieldsBuilder()
                                    .setPbb(new PbbBuilder().setPbbIsid(isid).build())
                                    .build())
                            .build())
                    .build())
            .setKey(new ActionKey(newActionKey))
            .build();
    }
}