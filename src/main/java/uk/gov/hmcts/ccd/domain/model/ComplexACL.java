package uk.gov.hmcts.ccd.domain.model;

public class ComplexACL extends AccessControlList {
    private String listElementCode;

    public String getListElementCode() {
        return listElementCode;
    }

    public void setListElementCode(String listElementCode) {
        this.listElementCode = listElementCode;
    }
}
