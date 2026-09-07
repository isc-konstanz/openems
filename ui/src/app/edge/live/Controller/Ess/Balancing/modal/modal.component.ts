// @ts-strict-ignore
import { Component, Input, OnInit } from "@angular/core";
import { FormBuilder, FormControl, FormGroup, Validators } from "@angular/forms";
import { ModalController } from "@ionic/angular";
import { TranslateService } from "@ngx-translate/core";
import { Edge, EdgeConfig, Service, Websocket } from "../../../../../../shared/shared";

@Component({
    selector: "balancing-modal",
    templateUrl: "./modal.component.html",
    standalone: false,
})
export class Controller_Symmetric_BalancingModalComponent implements OnInit {

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
            targetGridSetpoint: new FormControl(this.component.properties.targetGridSetpoint, Validators.compose([
                Validators.pattern("^-?(?:[1-9][0-9]*|0)$"),
                Validators.required,
            ])),
        });
    }

    applyChanges() {
        if (this.edge != null) {
            if (this.edge.roleIsAtLeast("owner")) {
                const targetGridSetpoint = this.formGroup.controls["targetGridSetpoint"];
                if (targetGridSetpoint.valid) {
                    const updateComponentArray = [];
                    Object.keys(this.formGroup.controls).forEach((element, index) => {
                        if (this.formGroup.controls[element].dirty) {
                            updateComponentArray.push({ name: Object.keys(this.formGroup.controls)[index], value: this.formGroup.controls[element].value });
                        }
                    });
                    this.loading = true;
                    this.edge.updateComponentConfig(this.websocket, this.component.id, updateComponentArray).then(() => {
                        this.component.properties.targetGridSetpoint = targetGridSetpoint.value;
                        this.loading = false;
                        this.service.toast(this.translate.instant("GENERAL.CHANGE_ACCEPTED"), "success");
                    }).catch(reason => {
                        targetGridSetpoint.setValue(this.component.properties.targetGridSetpoint);
                        this.loading = false;
                        this.service.toast(this.translate.instant("GENERAL.CHANGE_FAILED") + "\n" + reason.error.message, "danger");
                        console.warn(reason);
                    });
                    this.formGroup.markAsPristine();
                } else {
                    this.service.toast(this.translate.instant("GENERAL.INPUT_NOT_VALID"), "danger");
                }
            } else {
                this.service.toast(this.translate.instant("GENERAL.INSUFFICIENT_RIGHTS"), "danger");
            }
        }
    }
}
