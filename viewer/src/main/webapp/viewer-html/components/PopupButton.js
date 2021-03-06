/* 
 * Copyright (C) 2012 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Popup component
 * Creates a button which activates the generic popup.
 * @author <a href="mailto:meinetoonen@b3partners.nl">Meine Toonen</a>
 */
Ext.define ("viewer.components.PopupButton",{
    extend: "viewer.components.Component",
    //popup:null,
    button:null,
    config:{
        name: null,
        viewerController:null,
        label: ""
    },
    constructor: function (conf){        
        this.windowClosing = false;
        this.popup = conf.viewerController.layoutManager.popupWin;
        var selected = !this.popup.popupWin.isHidden() ||false ;
        viewer.components.Component.superclass.constructor.call(this, conf);
        this.initConfig(conf);
        var me =this;
        this.renderButton({
            handler: function(){
                me.popup.show();
                me.setButtonState('click',true);
            },
            text: me.title,
            icon: me.iconUrl,
            tooltip: me.tooltip,
            label: me.label
        });      
        var state = 'normal';
        if(selected){
            state = 'click';
        }
        this.setButtonState(state,true);
        this.popup.popupWin.addListener("hide",function(){me.setButtonState('normal',true);},this);
        return this;
    }
});
