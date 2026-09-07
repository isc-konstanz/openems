// @ts-strict-ignore
import { Component, Input, OnInit } from "@angular/core";
import { FormBuilder, FormControl, FormGroup, Validators } from "@angular/forms";
import { ModalController } from "@ionic/angular";
import { TranslateService } from "@ngx-translate/core";
import { Edge, EdgeConfig, Service, Websocket } from "../../../../../../shared/shared";

@Component({
    selector: "limittotaldischarge-modal",
    templateUrl: "./modal.component.html",
    standalone: false,
})
export class ControllerEssLimitTotalDischargeModalComponent implements OnInit {

    @Input({ required: true }) protected component!: EdgeConfig.Component;
    @Input({ required: true }) protected edge!: Edge;

    public formGroup: FormGroup;
    public loading: boolean = false;

    constructor(
        public formBuilder: FormBuilder,
        public modalCtrl: ModalController,
        public service: Service,
        public translate: TranslateService,
        public websocket: Websocket,
    ) { }

    ngOnInit() {
        this.formGroup = this.formBuilder.group({
            minSoc: new FormControl(this.component.properties.minSoc, Validators.compose([
                Validators.pattern("^(?:[1-9][0-9]*|0)$"),
                Validators.required,
            ])),
            forceChargeSoc: new FormControl(this.component.properties.forceChargeSoc, Validators.compose([
                Validators.pattern("^(?:[1-9][0-9]*|0)$"),
                Validators.required,
            ])),
            forceChargePower: new FormControl(this.component.properties.forceChargePower, Validators.compose([
                Validators.pattern("^(?:[1-9][0-9]*|0)?$"),
            ])),
        });
    }

    applyChanges() {
        if (this.edge != null) {
            if (this.edge.roleIsAtLeast("owner")) {
                const minSoc = this.formGroup.controls["minSoc"];
                const forceChargeSoc = this.formGroup.controls["forceChargeSoc"];
                const forceChargePower = this.formGroup.controls["forceChargePower"];
                if (minSoc.valid && forceChargeSoc.valid && forceChargePower.valid) {
                    if (minSoc.value >= forceChargeSoc.value) {
                        const updateComponentArray = [];
                        Object.keys(this.formGroup.controls).forEach((element, index) => {
                            if (this.formGroup.controls[element].dirty) {
                                updateComponentArray.push({ name: Object.keys(this.formGroup.controls)[index], value: this.formGroup.controls[element].value });
                            }
                        });
                        this.loading = true;
                        this.edge.updateComponentConfig(this.websocket, this.component.id, updateComponentArray).then(() => {
                            this.component.properties.minSoc = minSoc.value;
                            this.component.properties.forceChargeSoc = forceChargeSoc.value;
                            this.component.properties.forceChargePower = forceChargePower.value;
                            this.loading = false;
                            this.service.toast(this.translate.instant("GENERAL.CHANGE_ACCEPTED"), "success");
                        }).catch(reason => {
                            minSoc.setValue(this.component.properties.minSoc);
                            forceChargeSoc.setValue(this.component.properties.forceChargeSoc);
                            forceChargePower.setValue(this.component.properties.forceChargePower);
                            this.loading = false;
                            this.service.toast(this.translate.instant("GENERAL.CHANGE_FAILED") + "\n" + reason.error.message, "danger");
                            console.warn(reason);
                        });
                        this.formGroup.markAsPristine();
                    } else {
                        this.service.toast(this.translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.RELATION_ERROR"), "danger");
                    }
                } else {
                    this.service.toast(this.translate.instant("GENERAL.INPUT_NOT_VALID"), "danger");
                }
            } else {
                this.service.toast(this.translate.instant("GENERAL.INSUFFICIENT_RIGHTS"), "danger");
            }
        }
    }
}
