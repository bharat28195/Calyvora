package com.calyvora.document;

import java.util.List;

/**
 * The rest of the letter library (PD-63): every letter an Indian company issues across somebody's
 * time with it, beyond the first five starters. Seeded as set 2 — new companies get all of it, existing
 * ones get these added beside what they already have.
 *
 * <p>Written to be issued as they stand: gender-neutral throughout (a template that says "his" is
 * wrong for half the people it is sent to), numbers in Indian format and in words where a letter
 * states pay, and every company-specific term — probation, notice, hours, jurisdiction — read from the
 * company's own settings rather than typed into each letter. Fields only the issuer can know (a
 * reason, a new location) are {@code letter.*}-style fields the issue screen asks for.
 */
final class LetterLibrary {

    private LetterLibrary() {
    }

    private static final String SIGN = """
            Yours faithfully,

            **For {{company.name}}**

            {{signatory.name}}
            {{signatory.title}}""";

    private static final String SIGN_SINCERELY = """
            Sincerely,

            **For {{company.name}}**

            {{signatory.name}}
            {{signatory.title}}""";

    private static final String ACCEPT = """
            ---

            ## Acceptance

            I have read and understood the terms above and accept them.

            Signature: ______________________________

            Name: **{{employee.fullName}}**

            Date: ______________________________""";

    private static String body(String text) {
        return text.stripTrailing();
    }

    // =============================================================================================
    // Hiring
    // =============================================================================================

    static final StarterTemplates.Starter APPOINTMENT = new StarterTemplates.Starter(
            "Appointment letter",
            DocumentKind.APPOINTMENT_LETTER,
            "The full terms of employment — placement, hours, probation, leave, notice, confidentiality — with the salary annexure.",
            body("""
            {{today}}

            # Appointment letter

            Dear **{{employee.fullName}}**,

            We are glad to inform you that you have been appointed as **{{employee.jobTitle}}** in the {{employee.department}} department of **{{company.name}}** ("the Company"). Your date of joining is **{{employee.startDate}}** and your place of work is {{employee.workLocation}}.

            ## 1. Placement and compensation

            Your gross salary will be **{{salary.currency}} {{salary.annual}}** ({{salary.currency}} {{salary.annualInWords}}) a year, and your total cost to the Company **{{salary.currency}} {{salary.ctc}}** a year, as set out in Annexure I. The salary cycle runs from the first to the last day of each month, and salary is paid {{terms.payDay}}.

            ## 2. Place of work and transfer

            Your services may be transferred to any department, function or office of the Company, existing or future, anywhere in India, on a temporary or permanent basis. A transfer does not by itself change your remuneration, except for any allowance that applies to the new place or position under the Company's policies.

            ## 3. Responsibilities and conduct

            Your work will be subject to the Company's rules and regulations on conduct, discipline and other matters as in force from time to time. You are expected to carry out the duties of your position diligently and to the best of your ability.

            ## 4. Working days and hours

            The Company's working days are {{terms.workingDays}}, and working hours are {{terms.workingHours}}. Weekly working hours will not exceed the limits set by applicable labour law, and any overtime will be governed by it. Where you are posted at a client's site, you will follow that site's working hours.

            ## 5. Probation

            You will be on probation for **{{terms.probationDays}} days**, until **{{employee.probationEndDate}}**. On completion, your performance will be reviewed for confirmation. The Company may extend or shorten probation, and you remain on probation until confirmation is issued in writing.

            ## 6. Leave and attendance

            You are entitled to {{leave.summary}}. The leave year and rules are as per the Company's leave policy, aligned with the applicable Shops and Establishments Act and Labour Codes. Absence without prior intimation or approval is treated as leave without pay. Absence for three consecutive working days without any communication may be treated as abandonment of employment.

            ## 7. Salary revision

            Your remuneration may be reviewed from time to time based on your performance, the Company's performance and market conditions.

            ## 8. Notice period

            During probation, either party may end the employment with **{{terms.noticeProbation}}** notice. After confirmation, the notice period is **{{terms.noticePeriod}}** from either side. You may request early release by paying salary for the unserved part of the notice period; the Company may accept or decline such a request and may recover the amount from your final settlement, as permitted by law.

            ## 9. Information you have given

            If any declaration or information you have given the Company is found to be false, or material information is found to have been withheld, your services may be ended without notice.

            ## 10. Termination and return of property

            The Company may end your employment without notice for misconduct, as permitted by law. On leaving, you will return all Company property in your possession — laptop, storage, access cards and documents. The cost of property not returned may be recovered from amounts due to you.

            ## 11. Full and final settlement

            Your full and final settlement — unpaid wages, leave encashment, statutory dues and permitted recoveries — will be processed within the time required by law after your last working day, subject to completion of exit formalities and return of Company property.

            ## 12. Gratuity

            Gratuity is payable under the Payment of Gratuity Act, 1972 and the Code on Social Security, 2020, on completion of the qualifying service, and is calculated and paid as the law prescribes.

            ## General terms

            1. **Exclusivity.** During your employment you will devote your full working time to the Company and will not take up any activity that conflicts with your duties. Any conflict of interest must be disclosed immediately.
            2. **Policies.** You will comply with the Company's policies, procedures and rules as in force from time to time.
            3. **Confidentiality.** During and after your employment you will not use or disclose any confidential information, trade secret or data of the Company or its clients, except in the course of your work.
            4. **Intellectual property.** Every work, invention, design, process or improvement you create in the course of your employment belongs to the Company from the moment it is made, and you will sign whatever documents are needed to confirm this.
            5. **Non-solicitation.** For two years after leaving, you will not induce any employee of the Company to leave, nor solicit the Company's clients for work of the kind you did here.
            6. **Compensation is confidential.** The details of your compensation are confidential and are not to be discussed with other employees or third parties except as required by law.
            7. **Statutory deductions.** Income tax, professional tax, provident fund, ESI and other statutory deductions will be made as the law requires.
            8. **Governing law.** This appointment is governed by the laws of India, and the courts at {{terms.jurisdiction}} have jurisdiction.
            9. **Disputes.** Any grievance should first be raised through the Company's internal grievance mechanism, and otherwise resolved as provided by the Industrial Relations Code, 2020.
            10. **Entire agreement.** This letter supersedes all earlier discussions and arrangements about your employment. The Company may amend these terms to comply with changes in law, with written notice to you.

            Please confirm your acceptance by signing and returning a copy of this letter.

            """ + SIGN + """


            --- page ---

            # Annexure I — Salary structure

            **{{employee.fullName}} — {{employee.jobTitle}}**

            {{salary.structure}}

            Income tax, professional tax, provident fund and ESI are deducted as the law requires.

            """ + ACCEPT));

    static final StarterTemplates.Starter INTERNSHIP_OFFER = new StarterTemplates.Starter(
            "Internship offer letter",
            DocumentKind.INTERNSHIP_OFFER,
            "Offers an internship — duration, stipend, mentor, project and what happens at the end.",
            body("""
            {{today}}

            # Internship offer

            Dear **{{employee.firstName}}**,

            We are pleased to offer you an internship at **{{company.name}}** as **{{employee.jobTitle}}**.

            - **Starts:** {{employee.startDate}}
            - **Duration:** {{intern.duration}}
            - **Stipend:** {{salary.currency}} {{intern.stipend}} a month
            - **Mentor:** {{intern.mentor}}
            - **Project / area:** {{intern.project}}
            - **Location:** {{employee.workLocation}}
            - **Working hours:** {{terms.workingHours}}, {{terms.workingDays}}

            During the internship you will follow the Company's policies and keep confidential any information you come across. Work you produce during the internship belongs to the Company. Either side may end the internship with one week's notice.

            The internship does not by itself create an offer of employment. Interns who do well may be considered for a full-time role at the end of it.

            On completion you will receive an internship certificate. Please confirm your acceptance by signing and returning a copy of this letter.

            """ + SIGN + "\n\n" + ACCEPT));

    // =============================================================================================
    // Probation
    // =============================================================================================

    static final StarterTemplates.Starter CONFIRMATION = new StarterTemplates.Starter(
            "Confirmation letter",
            DocumentKind.CONFIRMATION_LETTER,
            "Confirms employment after probation is completed successfully.",
            body("""
            {{today}}

            # Confirmation of employment

            Dear **{{employee.fullName}}**,

            We are pleased to confirm that you have successfully completed your probation, and your employment as **{{employee.jobTitle}}** with **{{company.name}}** is confirmed with effect from **{{letter.effectiveDate}}**.

            From this date your notice period is **{{terms.noticePeriod}}**. All other terms of your appointment remain unchanged.

            Thank you for your work so far. We look forward to your continued contribution.

            """ + SIGN));

    static final StarterTemplates.Starter PROBATION_EXTENSION = new StarterTemplates.Starter(
            "Probation extension letter",
            DocumentKind.PROBATION_EXTENSION,
            "Extends probation, with the reason and what is expected by the new date.",
            body("""
            {{today}}

            # Extension of probation

            Dear **{{employee.fullName}}**,

            Your probation as **{{employee.jobTitle}}**, which began on {{employee.startDate}}, has been reviewed. We have decided to extend it by **{{probation.extensionMonths}}**, until **{{probation.extendedUntil}}**.

            **Reason for the extension:** {{letter.reason}}

            **What we expect by the end of the extended period:**

            {{letter.details}}

            Your manager, {{employee.manager}}, will review progress with you regularly. Your notice period during probation remains {{terms.noticeProbation}}, and all other terms of your appointment are unchanged.

            """ + SIGN));

    // =============================================================================================
    // Growth
    // =============================================================================================

    static final StarterTemplates.Starter INCREMENT = new StarterTemplates.Starter(
            "Salary increment letter",
            DocumentKind.INCREMENT_LETTER,
            "Announces a salary revision — the new figure, the increase and the revised structure.",
            body("""
            {{today}}

            # Salary increment letter

            Dear **{{employee.fullName}}**,

            We are delighted to recognise your hard work, dedication and contributions to {{company.name}}. Your commitment and consistent effort have played an important part in our success, and we appreciate the impact you have made.

            As a token of our appreciation, your compensation has been revised. With effect from **{{salary.effectiveDate}}**, your revised annual salary will be **{{salary.currency}} {{salary.annual}}** ({{salary.currency}} {{salary.annualInWords}}) — an increase of {{salary.currency}} {{salary.increase}} ({{salary.increasePercent}}) on your previous {{salary.currency}} {{salary.previousAnnual}}.

            This revision reflects our confidence in your abilities. We look forward to seeing you reach even greater milestones with us.

            Congratulations, and we wish you continued success!

            The revised salary structure is set out below.

            {{salary.takeHome}}

            """ + SIGN));

    static final StarterTemplates.Starter TRANSFER = new StarterTemplates.Starter(
            "Transfer letter",
            DocumentKind.TRANSFER_LETTER,
            "Moves an employee to another location or team.",
            body("""
            {{today}}

            # Transfer letter

            Dear **{{employee.fullName}}**,

            In line with business requirements, you are transferred from **{{transfer.fromLocation}}** to **{{transfer.toLocation}}** with effect from **{{letter.effectiveDate}}**. From that date you will report to {{transfer.reportingTo}}.

            Your designation remains **{{employee.jobTitle}}**, and your salary and other terms of employment are unchanged except for any allowance that applies to the new location under Company policy.

            {{letter.details}}

            Please complete the handover of your current responsibilities before the transfer date. We wish you every success in your new role.

            """ + SIGN));

    static final StarterTemplates.Starter BONUS = new StarterTemplates.Starter(
            "Bonus / variable pay letter",
            DocumentKind.BONUS_LETTER,
            "Announces a bonus or variable payout and when it will be paid.",
            body("""
            {{today}}

            # Bonus letter

            Dear **{{employee.fullName}}**,

            In recognition of your performance and contribution during **{{bonus.period}}**, we are pleased to award you a bonus of **{{salary.currency}} {{bonus.amount}}**.

            The bonus will be paid with your salary for **{{bonus.payoutDate}}**, subject to applicable tax deductions. It is a one-time payment and does not form part of your fixed salary.

            Thank you for your efforts. Congratulations!

            """ + SIGN_SINCERELY));

    static final StarterTemplates.Starter APPRECIATION = new StarterTemplates.Starter(
            "Appreciation letter",
            DocumentKind.APPRECIATION_LETTER,
            "Thanks an employee in writing for a specific piece of work.",
            body("""
            {{today}}

            # Letter of appreciation

            Dear **{{employee.firstName}}**,

            On behalf of {{company.name}}, I want to thank you for your outstanding work.

            {{letter.details}}

            Contributions like yours make a real difference to the team and to the people we serve. We are proud to have you with us.

            With appreciation,

            {{signatory.name}}
            {{signatory.title}}
            {{company.name}}"""));

    // =============================================================================================
    // Verification
    // =============================================================================================

    static final StarterTemplates.Starter SALARY_CERTIFICATE = new StarterTemplates.Starter(
            "Salary certificate",
            DocumentKind.SALARY_CERTIFICATE,
            "For a bank, landlord or embassy — current salary and the last three payslips.",
            body("""
            {{today}}

            # Salary certificate

            **To:** {{letter.addressedTo}}

            This is to certify that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}) has been employed with **{{company.name}}** since **{{employee.startDate}}** and is currently working as **{{employee.jobTitle}}**.

            Their current gross salary is **{{salary.currency}} {{salary.annual}}** a year ({{salary.currency}} {{salary.monthly}} a month). The salary paid in recent months is:

            {{payslips.recent}}

            This certificate is issued at the employee's request for **{{letter.purpose}}** and does not create any liability for the Company.

            """ + SIGN));

    static final StarterTemplates.Starter EMPLOYMENT_VERIFICATION = new StarterTemplates.Starter(
            "Employment and address verification letter",
            DocumentKind.EMPLOYMENT_VERIFICATION,
            "Confirms current employment and residential address — for a bank, passport or rental.",
            body("""
            {{today}}

            # To whom it may concern

            This is to certify that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}) is a full-time employee of **{{company.name}}**, working as **{{employee.jobTitle}}** in the {{employee.department}} department since **{{employee.startDate}}**.

            As per our records, their residential address is:

            {{letter.residentialAddress}}

            This letter is issued at the employee's request for **{{letter.purpose}}**.

            """ + SIGN));

    static final StarterTemplates.Starter NOC = new StarterTemplates.Starter(
            "No objection certificate (NOC)",
            DocumentKind.NOC,
            "The Company has no objection — for a visa, higher studies or similar.",
            body("""
            {{today}}

            # No objection certificate

            **To:** {{letter.addressedTo}}

            This is to certify that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}) has been employed with **{{company.name}}** as **{{employee.jobTitle}}** since **{{employee.startDate}}**.

            The Company has no objection to {{employee.firstName}} **{{letter.purpose}}**. {{letter.details}}

            Their employment and the terms of it continue unchanged. This certificate does not create any financial or other liability for the Company.

            """ + SIGN));

    // =============================================================================================
    // Discipline
    // =============================================================================================

    static final StarterTemplates.Starter WARNING = new StarterTemplates.Starter(
            "Warning letter",
            DocumentKind.WARNING_LETTER,
            "A written warning about conduct or performance, and what has to change.",
            body("""
            {{today}}

            **Private and confidential**

            # Warning letter

            Dear **{{employee.fullName}}**,

            This letter is a formal written warning regarding **{{letter.reason}}**.

            {{letter.details}}

            This falls short of the standards the Company expects under its policies and your terms of employment. We expect an immediate and sustained improvement. A repetition, or any further breach, may lead to stronger disciplinary action, up to and including termination of employment.

            If you would like to discuss this or need support to meet these expectations, please speak with {{employee.manager}} or HR. A copy of this letter will be kept on your file.

            """ + SIGN + """


            ---

            I acknowledge receipt of this letter.

            Signature: ______________________________   Date: ______________"""));

    static final StarterTemplates.Starter SHOW_CAUSE = new StarterTemplates.Starter(
            "Show-cause notice",
            DocumentKind.SHOW_CAUSE_NOTICE,
            "Asks an employee to explain an alleged lapse in writing before any action is decided.",
            body("""
            {{today}}

            **Private and confidential**

            # Show-cause notice

            Dear **{{employee.fullName}}**,

            It has come to the Company's notice that **{{letter.reason}}**.

            {{letter.details}}

            If established, this would amount to misconduct under the Company's policies and your terms of employment. You are asked to explain in writing, within **{{letter.responseDays}} days** of receiving this notice, why disciplinary action should not be taken against you.

            If no reply is received in that time, the Company will assume you have nothing to say in your defence and will decide the matter on the information available. This notice is not a finding against you; your explanation will be considered fully before any decision is made.

            """ + SIGN));

    static final StarterTemplates.Starter PIP = new StarterTemplates.Starter(
            "Performance improvement plan (PIP)",
            DocumentKind.PIP_LETTER,
            "Sets clear goals, support and a review date for improving performance.",
            body("""
            {{today}}

            **Private and confidential**

            # Performance improvement plan

            Dear **{{employee.fullName}}**,

            Following recent reviews of your work as **{{employee.jobTitle}}**, we have identified areas where performance is below what the role requires: {{letter.reason}}.

            To support you in improving, you are being placed on a performance improvement plan from **{{pip.startDate}}** to **{{pip.endDate}}**.

            **Goals to be met by the end of the plan:**

            {{pip.goals}}

            **Support you will receive:** regular check-ins with {{employee.manager}}, feedback on progress, and any training agreed between you.

            At the end of the plan your performance will be reviewed. Meeting the goals closes the plan. Not meeting them may lead to further action, including ending your employment in line with your terms and the law.

            We want you to succeed and will support you through this plan.

            """ + SIGN + """


            ---

            I acknowledge receipt of this plan.

            Signature: ______________________________   Date: ______________"""));

    static final StarterTemplates.Starter TERMINATION = new StarterTemplates.Starter(
            "Termination letter",
            DocumentKind.TERMINATION_LETTER,
            "Ends employment — the reason, last working day, notice and settlement.",
            body("""
            {{today}}

            **Private and confidential**

            # Termination of employment

            Dear **{{employee.fullName}}**,

            This is to inform you that your employment with **{{company.name}}** as **{{employee.jobTitle}}** is terminated with effect from **{{letter.effectiveDate}}**, which will be your last working day.

            **Reason:** {{letter.reason}}

            {{letter.details}}

            Your full and final settlement — salary up to your last working day, leave encashment, gratuity where applicable and any statutory dues, less permitted recoveries — will be processed within the time required by law. Please return all Company property, including your laptop, access card and documents, before your last working day.

            Your obligations of confidentiality and non-solicitation continue after your employment ends.

            """ + SIGN));

    // =============================================================================================
    // Exit
    // =============================================================================================

    static final StarterTemplates.Starter RESIGNATION_ACCEPTANCE = new StarterTemplates.Starter(
            "Resignation acceptance letter",
            DocumentKind.RESIGNATION_ACCEPTANCE,
            "Accepts a resignation and confirms the last working day and exit steps.",
            body("""
            {{today}}

            # Acceptance of resignation

            Dear **{{employee.fullName}}**,

            We acknowledge receipt of your resignation dated **{{resignation.date}}** from the position of **{{employee.jobTitle}}**. Your resignation is accepted, and your last working day with **{{company.name}}** will be **{{employee.endDate}}**.

            Before your last working day, please:

            - complete the handover of your work to {{employee.manager}};
            - return all Company property — laptop, access card, documents and any other equipment;
            - complete the exit formalities shared by HR.

            Your full and final settlement will be processed after your last working day, and your relieving and experience letters will be issued once clearance is complete.

            Thank you for your contribution to {{company.name}}. We wish you every success.

            """ + SIGN));

    static final StarterTemplates.Starter NO_DUES = new StarterTemplates.Starter(
            "No dues certificate",
            DocumentKind.NO_DUES_CERTIFICATE,
            "Confirms a departing employee owes the Company nothing — property returned, advances cleared.",
            body("""
            {{today}}

            # No dues certificate

            This is to certify that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}), **{{employee.jobTitle}}** in the {{employee.department}} department, whose last working day was **{{employee.endDate}}**, has:

            - returned all Company property issued to them, including equipment, access cards and documents;
            - no outstanding loans, advances or other amounts payable to the Company;
            - completed the handover of their responsibilities.

            Accordingly, the Company has no dues outstanding against {{employee.firstName}} as of the date of this certificate.

            """ + SIGN));

    static final StarterTemplates.Starter FNF = new StarterTemplates.Starter(
            "Full and final settlement statement",
            DocumentKind.FNF_STATEMENT,
            "Lists every amount paid and recovered at exit, and the net settled.",
            body("""
            {{today}}

            # Full and final settlement

            **Employee:** {{employee.fullName}} (Employee ID {{employee.employeeNo}})

            **Designation:** {{employee.jobTitle}}, {{employee.department}}

            **Date of joining:** {{employee.startDate}}     **Last working day:** {{employee.endDate}}

            | PARTICULARS | AMOUNT ({{salary.currency}}) |
            |---|---|
            | Salary for days worked | {{fnf.salaryDue}} |
            | Leave encashment | {{fnf.leaveEncashment}} |
            | Gratuity | {{fnf.gratuity}} |
            | Bonus and other dues | {{fnf.bonus}} |
            | Less: recoveries (notice shortfall, assets, advances) | {{fnf.recoveries}} |
            | **Net amount payable** | **{{fnf.netPayable}}** |

            The net amount has been / will be paid to your registered bank account on **{{fnf.paymentDate}}**, after deduction of applicable taxes. With this payment, all dues between you and {{company.name}} are settled in full.

            """ + SIGN + """


            ---

            I confirm that I have received the above amount in full and final settlement of all my dues, and I have no further claim against the Company.

            Signature: ______________________________   Date: ______________"""));

    static final StarterTemplates.Starter INTERNSHIP_CERTIFICATE = new StarterTemplates.Starter(
            "Internship completion certificate",
            DocumentKind.INTERNSHIP_CERTIFICATE,
            "Certifies an internship — dates, area of work and conduct.",
            body("""
            {{today}}

            # Internship certificate

            **To whom it may concern**

            This is to certify that **{{employee.fullName}}** completed an internship with **{{company.name}}** as **{{employee.jobTitle}}** from **{{employee.startDate}}** to **{{employee.endDate}}**.

            During the internship, {{employee.firstName}} worked on **{{intern.project}}** under the guidance of {{intern.mentor}}. {{letter.details}}

            We found {{employee.firstName}} sincere, hard-working and a good member of the team, and we wish them every success.

            """ + SIGN));

    static final List<StarterTemplates.Starter> SET_2 = List.of(
            APPOINTMENT, INTERNSHIP_OFFER, CONFIRMATION, PROBATION_EXTENSION, INCREMENT, TRANSFER, BONUS,
            APPRECIATION, SALARY_CERTIFICATE, EMPLOYMENT_VERIFICATION, NOC, WARNING, SHOW_CAUSE, PIP,
            TERMINATION, RESIGNATION_ACCEPTANCE, NO_DUES, FNF, INTERNSHIP_CERTIFICATE)
            .stream().map(s -> new StarterTemplates.Starter(s.name(), s.kind(), s.description(), s.body(), 2))
            .toList();
}
