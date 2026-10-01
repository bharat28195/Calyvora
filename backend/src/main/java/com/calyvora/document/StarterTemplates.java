package com.calyvora.document;

import java.util.List;

/**
 * The starter template library (feedback D3). Seeded per-company the first time Documents is opened,
 * then fully editable — a company's letters should read like *their* letters, not ours.
 * Placeholders use {@code {{merge.field}}} and are resolved by {@link MergeFields}.
 */
final class StarterTemplates {

    record Starter(String name, DocumentKind kind, String description, String body) {}

    private StarterTemplates() {
    }

    static List<Starter> all() {
        return List.of(OFFER, JOINING, RELIEVING, EXPERIENCE, PROMOTION);
    }

    private static final Starter OFFER = new Starter(
            "Offer letter",
            DocumentKind.OFFER_LETTER,
            "Extends a formal offer with role, start date and compensation.",
            """
            {{company.name}}

            {{today}}

            **Private & confidential**

            Dear {{employee.firstName}},

            We are delighted to offer you the position of **{{employee.jobTitle}}** at {{company.name}}.

            - **Role:** {{employee.jobTitle}}
            - **Department:** {{employee.department}}
            - **Employment type:** {{employee.employmentType}}
            - **Start date:** {{employee.startDate}}
            - **Location:** {{employee.workLocation}}
            - **Annual compensation:** {{salary.currency}} {{salary.annual}}

            Your appointment is subject to our standard terms of employment. We believe your
            experience will be a strong addition to the team and we look forward to working with you.

            Please confirm your acceptance by signing and returning a copy of this letter.

            Warm regards,

            {{signatory.name}}
            {{signatory.title}}
            {{company.name}}
            """);

    private static final Starter JOINING = new Starter(
            "Joining letter",
            DocumentKind.JOINING_LETTER,
            "Confirms that an employee has joined, with role and start date.",
            """
            {{company.name}}

            {{today}}

            **To whom it may concern**

            This is to confirm that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}) has
            joined {{company.name}} as **{{employee.jobTitle}}** in the {{employee.department}} department,
            effective **{{employee.startDate}}**.

            {{employee.firstName}} is based at {{employee.workLocation}} and reports to {{employee.manager}}.

            We warmly welcome {{employee.firstName}} to the team and wish them a successful tenure with us.

            Sincerely,

            {{signatory.name}}
            {{signatory.title}}
            {{company.name}}
            """);

    private static final Starter RELIEVING = new Starter(
            "Relieving letter",
            DocumentKind.RELIEVING_LETTER,
            "Issued on exit — confirms the last working day and clearance.",
            """
            {{company.name}}

            {{today}}

            **To whom it may concern**

            This is to certify that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}) was
            employed with {{company.name}} as **{{employee.jobTitle}}** from **{{employee.startDate}}**
            to **{{employee.endDate}}**.

            {{employee.firstName}} has been relieved of their duties with effect from the close of
            business on {{employee.endDate}}. All company property has been returned and dues settled.

            We thank {{employee.firstName}} for their contribution and wish them every success ahead.

            Sincerely,

            {{signatory.name}}
            {{signatory.title}}
            {{company.name}}
            """);

    private static final Starter EXPERIENCE = new Starter(
            "Experience certificate",
            DocumentKind.EXPERIENCE_LETTER,
            "Certifies tenure, role and conduct for a departing employee.",
            """
            {{company.name}}

            {{today}}

            **To whom it may concern**

            This is to certify that **{{employee.fullName}}** served {{company.name}} as
            **{{employee.jobTitle}}** in the {{employee.department}} department from
            **{{employee.startDate}}** to **{{employee.endDate}}** ({{employee.tenure}}).

            During this period we found {{employee.firstName}} to be diligent, professional and
            well regarded by colleagues. Their conduct throughout the engagement was satisfactory.

            This certificate is issued on request.

            Sincerely,

            {{signatory.name}}
            {{signatory.title}}
            {{company.name}}
            """);

    private static final Starter PROMOTION = new Starter(
            "Promotion / increment letter",
            DocumentKind.PROMOTION_LETTER,
            "Confirms a new title and revised compensation.",
            """
            {{company.name}}

            {{today}}

            Dear {{employee.firstName}},

            In recognition of your performance and contribution, we are pleased to confirm your
            revised role and compensation at {{company.name}}.

            - **Revised designation:** {{employee.jobTitle}}
            - **Department:** {{employee.department}}
            - **Revised annual compensation:** {{salary.currency}} {{salary.annual}}
            - **Effective from:** {{salary.effectiveDate}}

            All other terms of your employment remain unchanged. Congratulations, and thank you for
            the work you continue to put in.

            Warm regards,

            {{signatory.name}}
            {{signatory.title}}
            {{company.name}}
            """);
}
