/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.gui.core.widget;

import lk.vexview.gui.core.VexTextBox;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

public class VexTextFieldWidget
extends GuiTextField {
    public int f1;
    VexTextBox f2;

    public VexTextFieldWidget(int n, FontRenderer fontRenderer, int n2, int n3, int n4, int n5, VexTextBox vexTextBox) {
        super(n, fontRenderer, n2, n3, n4, n5);
        VexTextFieldWidget vexTextFieldWidget = this;
        this.f1 = n;
        this.f2 = vexTextBox;
        this.setMaxStringLength(vexTextBox.f5);
        vexTextFieldWidget.setFocused(false);
        vexTextFieldWidget.setText(vexTextBox.f9);
    }

    public boolean getEnableBackgroundDrawing() {
        return !this.f2.f10;
    }

    public void resetPosition(int n, int n2, int n3, int n4) {
        this.xPosition = n + (this.f2.f1 == -1 ? this.width / 2 - this.f2.f2 / 2 : this.f2.f1);
        this.yPosition = n2 + (this.f2.f3 == -1 ? this.height / 2 - this.f2.f4 / 2 : this.f2.f3);
    }

    public void drawTextBox() {
        if (this.f2.f10) {
            VexTextFieldWidget vexTextFieldWidget = this;
            VexTextFieldWidget vexTextFieldWidget2 = this;
            VexTextFieldWidget vexTextFieldWidget3 = this;
            VexTextFieldWidget.drawRect((int)vexTextFieldWidget.xPosition, (int)vexTextFieldWidget2.yPosition, (int)(vexTextFieldWidget3.xPosition + vexTextFieldWidget3.width), (int)(this.yPosition + 1), (int)this.f2.f8);
            VexTextFieldWidget vexTextFieldWidget4 = this;
            VexTextFieldWidget vexTextFieldWidget5 = this;
            VexTextFieldWidget vexTextFieldWidget6 = this;
            VexTextFieldWidget.drawRect((int)vexTextFieldWidget.xPosition, (int)(vexTextFieldWidget4.yPosition + vexTextFieldWidget4.height - 1), (int)(vexTextFieldWidget5.xPosition + vexTextFieldWidget5.width), (int)(vexTextFieldWidget6.yPosition + vexTextFieldWidget6.height), (int)this.f2.f8);
            VexTextFieldWidget vexTextFieldWidget7 = this;
            VexTextFieldWidget vexTextFieldWidget8 = this;
            VexTextFieldWidget.drawRect((int)vexTextFieldWidget2.xPosition, (int)vexTextFieldWidget7.yPosition, (int)(vexTextFieldWidget7.xPosition + 1), (int)(vexTextFieldWidget8.yPosition + vexTextFieldWidget8.height), (int)this.f2.f8);
            VexTextFieldWidget vexTextFieldWidget9 = this;
            VexTextFieldWidget vexTextFieldWidget10 = this;
            VexTextFieldWidget.drawRect((int)(vexTextFieldWidget.xPosition + this.width - 1), (int)vexTextFieldWidget9.yPosition, (int)(vexTextFieldWidget9.xPosition + this.width), (int)(vexTextFieldWidget10.yPosition + vexTextFieldWidget10.height), (int)this.f2.f8);
            VexTextFieldWidget vexTextFieldWidget11 = this;
            VexTextFieldWidget vexTextFieldWidget12 = this;
            VexTextFieldWidget.drawRect((int)(vexTextFieldWidget.xPosition + 1), (int)(this.yPosition + 1), (int)(vexTextFieldWidget11.xPosition + vexTextFieldWidget11.width - 1), (int)(vexTextFieldWidget12.yPosition + vexTextFieldWidget12.height - 1), (int)this.f2.f7);
        }
        super.drawTextBox();
    }
}

