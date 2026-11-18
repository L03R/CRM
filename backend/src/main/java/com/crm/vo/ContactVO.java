package com.crm.vo;
import com.crm.entity.Contact;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
@Data
@EqualsAndHashCode(callSuper=true)
public class ContactVO extends Contact {
    private String companyName;
    private String ownerName;
    private List<String> actions=new ArrayList<>();
}
