// @ts-strict-ignore
import { Component } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";

import { Modal } from "src/app/shared/components/flat/flat";
import { ChannelAddress, CurrentData, Utils } from "../../../../../shared/shared";
import { ControllerEssLimitTotalDischargeModalComponent } from "./modal/modal.component";

@Component({
    selector: "Controller_Ess_LimitTotalDischarge",
    templateUrl: "./LimitTotalDischarge.html",
    standalone: false,
})
export class ControllerEssLimitTotalDischargeComponent extends AbstractFlatWidget {

    public soc: number;
    public minSocSlider: number;
    public forceChargeSoc: number;
    public forceChargePower: number;
    public readonly CONVERT_WATT_TO_KILOWATT = Utils.CONVERT_WATT_TO_KILOWATT;

    protected isSliderActive: boolean = false;

    protected modalComponent: Modal | null = null;
    protected override afterIsInitialized(): void {
        this.modalComponent = this.getModalComponent();
        this.minSocSlider = this.component.properties["minSoc"];
        this.forceChargeSoc = this.component.properties["forceChargeSoc"];
        this.forceChargePower = this.component.properties["forceChargePower"];
    }
    protected getModalComponent(): Modal {
        return {
            component: ControllerEssLimitTotalDischargeModalComponent,
            componentProps: {
                component: this.component,
                edge: this.edge,
            },
        };
    };

    protected override getChannelAddresses() {
        return [
            new ChannelAddress(this.component.properties["ess.id"], "Soc"),
        ];
    }

    protected override onCurrentData(currentData: CurrentData) {
        this.soc = currentData.allComponents[this.component.properties["ess.id"] + "/Soc"];
        this.forceChargeSoc = this.component.properties["forceChargeSoc"];
        this.forceChargePower = this.component.properties["forceChargePower"];
        if (!this.isSliderActive) {
            this.minSocSlider = this.component.properties["minSoc"];
        }
    }

    protected onMinSocChange(value: number) {
        this.isSliderActive = false;
        if (value === this.component.properties["minSoc"]) {
            return;
        }
        this.edge.updateComponentConfig(this.websocket, this.component.id, [{ name: "minSoc", value: value }]).then(() => {
            this.component.properties["minSoc"] = value;
            this.service.toast(this.translate.instant("GENERAL.CHANGE_ACCEPTED"), "success");
        }).catch(reason => {
            this.minSocSlider = this.component.properties["minSoc"];
            this.service.toast(this.translate.instant("GENERAL.CHANGE_FAILED") + "\n" + reason.error.message, "danger");
            console.warn(reason);
        });
    }

}
