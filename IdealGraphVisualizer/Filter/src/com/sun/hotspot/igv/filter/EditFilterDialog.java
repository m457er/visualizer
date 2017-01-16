/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */
package com.sun.hotspot.igv.filter;

import org.openide.windows.WindowManager;

/**
 *
 * @author Thomas Wuerthinger
 */
public class EditFilterDialog extends javax.swing.JDialog {

    private CustomFilter customFilter;
    private boolean accepted;

    /** Creates new form EditFilterDialog */
    public EditFilterDialog(CustomFilter customFilter) {
        super(WindowManager.getDefault().getMainWindow(), true);
        this.customFilter = customFilter;
        initComponents();

        sourceTextArea.setText(customFilter.getCode());
        nameTextField.setText(customFilter.getName());
    }

    public boolean wasAccepted() {
        return accepted;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        sourceTextArea = new javax.swing.JTextArea();
        nameTextField = new javax.swing.JTextField();
        nameLabel = new javax.swing.JLabel();
        sourceLabel = new javax.swing.JLabel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "title")); // NOI18N
        setResizable(false);

        sourceTextArea.setColumns(20);
        sourceTextArea.setRows(5);
        jScrollPane1.setViewportView(sourceTextArea);

        nameTextField.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "nameTextField.text")); // NOI18N

        nameLabel.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jLabel1.text")); // NOI18N

        sourceLabel.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jLabel2.text")); // NOI18N

        okButton.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jButton1.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonClicked(evt);
                okButtonClicked(evt);
            }
        });

        cancelButton.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jButton2.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonClicked(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(sourceLabel)
                            .addComponent(nameLabel))
                        .addGap(25, 25, 25)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 695, Short.MAX_VALUE)
                            .addComponent(nameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 695, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 76, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLabel)
                    .addComponent(nameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sourceLabel)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 16, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

private void okButtonClicked(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonClicked
    this.customFilter.setName(this.nameTextField.getText());
    this.customFilter.setCode(this.sourceTextArea.getText());
    accepted = true;
    setVisible(false);
}//GEN-LAST:event_okButtonClicked

private void cancelButtonClicked(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonClicked
    setVisible(false);
}//GEN-LAST:event_cancelButtonClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JTextField nameTextField;
    private javax.swing.JButton okButton;
    private javax.swing.JLabel sourceLabel;
    private javax.swing.JTextArea sourceTextArea;
    // End of variables declaration//GEN-END:variables

}
