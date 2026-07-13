// @ts-strict-ignore
import { Component } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";

import { Modal } from "src/app/shared/components/flat/flat";
import { CurrentData, Utils } from "../../../../../shared/shared";
import { Controller_Symmetric_BalancingModalComponent } from "./modal/modal.component";

@Component({
    selector: "Controller_Symmetric_Balancing",
    templateUrl: "./Balancing.html",
    standalone: false,
})
export class Controller_Symmetric_BalancingComponent extends AbstractFlatWidget {

    public targetGridSetpoint: number;
    public readonly CONVERT_WATT_TO_KILOWATT = Utils.CONVERT_WATT_TO_KILOWATT;

    protected modalComponent: Modal | null = null;
    protected override afterIsInitialized(): void {
        this.modalComponent = this.getModalComponent();
        this.targetGridSetpoint = this.component.properties["targetGridSetpoint"];
    }
    protected getModalComponent(): Modal {
        return {
            component: Controller_Symmetric_BalancingModalComponent,
            componentProps: {
                component: this.component,
                edge: this.edge,
            },
        };
    };

    protected override onCurrentData(currentData: CurrentData) {
        this.targetGridSetpoint = this.component.properties["targetGridSetpoint"];
    }

}
