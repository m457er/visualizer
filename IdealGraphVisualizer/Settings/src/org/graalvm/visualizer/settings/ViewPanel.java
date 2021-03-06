/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package org.graalvm.visualizer.settings;

final class ViewPanel extends javax.swing.JPanel {

    private final ViewOptionsPanelController controller;

    ViewPanel(ViewOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT
     * modify this code. The content of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        portSpinner = new javax.swing.JSpinner();
        jScrollPane1 = new javax.swing.JScrollPane();
        nodeTextArea = new javax.swing.JTextArea();
        nodeWidthSpinner = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, "Node Text");

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, "Node Width");

        nodeTextArea.setColumns(20);
        nodeTextArea.setRows(5);
        jScrollPane1.setViewportView(nodeTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, "Network Port");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
                        jPanel1Layout.createParallelGroup(
                                        javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                                                        javax.swing.GroupLayout.Alignment.TRAILING,
                                                        jPanel1Layout.createSequentialGroup().addContainerGap().addGroup(jPanel1Layout.createParallelGroup(
                                                                        javax.swing.GroupLayout.Alignment.LEADING).addComponent(jLabel1).addComponent(jLabel3).addComponent(jLabel2)).addGap(39,
                                                                                        39,
                                                                                        39).addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(
                                                                                                        portSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 63,
                                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(nodeWidthSpinner,
                                                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE, 63,
                                                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(jScrollPane1,
                                                                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE, 365,
                                                                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE)).addContainerGap()));
        jPanel1Layout.setVerticalGroup(
                        jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(jPanel1Layout.createSequentialGroup().addContainerGap().addGroup(
                                        jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(jPanel1Layout.createSequentialGroup().addComponent(jScrollPane1,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE).addGap(18,
                                                                        18, 18).addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(nodeWidthSpinner,
                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(jLabel2)).addGap(18, 18, 18).addGroup(
                                                                                                        jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(
                                                                                                                        portSpinner, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(
                                                                                                                                        jLabel3))).addComponent(jLabel1)).addGap(73, 73, 73)));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addContainerGap().addComponent(jPanel1,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addContainerGap()));
        layout.setVerticalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addContainerGap().addComponent(jPanel1,
                                        javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE).addContainerGap(206, Short.MAX_VALUE)));
    }// </editor-fold>//GEN-END:initComponents

    void load() {
        nodeTextArea.setText(Settings.get().get(Settings.NODE_TEXT, Settings.NODE_TEXT_DEFAULT));
        nodeWidthSpinner.setValue(Integer.parseInt(Settings.get().get(Settings.NODE_WIDTH, Settings.NODE_WIDTH_DEFAULT)));
        portSpinner.setValue(Integer.parseInt(Settings.get().get(Settings.PORT, Settings.PORT_DEFAULT)));
    }

    void store() {
        Settings.get().put(Settings.NODE_TEXT, nodeTextArea.getText());
        Settings.get().put(Settings.NODE_WIDTH, nodeWidthSpinner.getValue().toString());
        Settings.get().put(Settings.PORT, portSpinner.getValue().toString());
    }

    boolean valid() {
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea nodeTextArea;
    private javax.swing.JSpinner nodeWidthSpinner;
    private javax.swing.JSpinner portSpinner;
    // End of variables declaration//GEN-END:variables
}
