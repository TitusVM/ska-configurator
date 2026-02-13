# 1. Application Definition

## 1.1 Introduction

Since 2010, Switzerland has issued passports and travel documents for foreign nationals equipped with an electronic chip. This chip contains, among other things, the holder’s photograph and up to two fingerprints stored in electronic form. Since 2011, residence permits for foreign nationals have also been equipped with a chip containing electronically stored data.

To guarantee and verify the authenticity and origin of the data stored on this chip, the data must be signed using an electronic signature. This responsibility lies with the organization. Access to the stored fingerprints is secured using the organization and is granted only to authorized parties and to authorities that comply with the provisions of Regulation (EC) No. 2252/2004.

The the organization creates and manages, on the one hand, the keys and certificates used for the electronic signature of the data, and on the other hand, the software tools required to generate and verify these electronic signatures.

The the organization organization is responsible for the tasks mentioned above. In order to ensure various aspects of information security, it is essential to define strict requirements for control mechanisms. An adversary with access to the infrastructure could otherwise produce forged documents that would be indistinguishable from authentic ones.

One of the controls to be implemented at all operational levels—whether for a simple device update task or for managing roles between departments—is segregation of duties (SOD). This control prevents a single individual from manipulating a system without supervision. In other words, no one should be able to act alone: every significant action must be validated or co-signed by at least one other person.

The **Smart Key Attributes (SKA)** configurations are used to configure certain critical devices. Once configured, these devices can only be used if the conditions defined in the SKA are met—meaning only when segregation of duties is enforced.

The objective of this project is to create an application for generating SKA configurations that provides a user-friendly experience while meeting high requirements in terms of resilience and completeness of the validation process.

---

# 2. Detailed Requirements

## 2.1 Objectives

### 2.1.1 Primary Objectives

- Create and/or configure an SKA configuration file through a graphical user interface:
  - User groups and quorums  
  - Import users via graphical interface (by importing a CSV file)
  - Change user certificates
  - Modify curve parameters

## 2.2 Constraints

### 2.2.1 Technologies

- The application must be written in Java using Swing for the graphical user interface.  
- The application must be executable on target computers (no `.exe` file dependencies, runnable `.jar` is fine).  
- The application must be able to import jira CSV asset exports (just like the one found in `/exports/export.csv`)
- The application must be capable of generating SKA configuration files in the format defined by the organization. An example of such a configuration file can be found in `/examples/ska.xml`
- The application must be easy to use and provide a user-friendly experience, with clear instructions and feedback for the user

---
### 2.2.2 Software Engineering Principles

- The KISS (Keep It Simple, Stupid), YAGNI (You Aren’t Gonna Need It), and DRY (Don’t Repeat Yourself) principles must be followed.  
- Dependency management must be clearly defined.  
- The codebase must be maintainable and modular.  
- The project must include documentation and a functional verification checklist.  

---
### 2.2.3 Usability Requirements

- The application must provide clear validation feedback for user actions.  
- Destructive or critical operations must require confirmation.  
- Error messages must be explicit and actionable.  
- The user interface must remain simple and consistent, following KISS principles.  

---

### 2.2.4 Security Requirements

- All external inputs (CSV, certificates, parameters) must be validated.  
- XML generation must prevent injection or malformed structure issues.  
- Certificates must be securely parsed and validated (e.g., X.509 structure validation).  
- No hardcoded secrets or credentials in the source code.  
- Proper exception handling and secure logging practices must be implemented.  
- Sensitive data must not be exposed in logs or UI error messages.  

---

### 2.2.5 SKA Output Requirements

- Generated XML files must strictly follow the format defined by the organization.  
- If an XML schema (XSD) is available, generated files must validate against it.  
- Output must be deterministic (identical input produces identical output).  
- Generated files must be human-readable (proper indentation).  

---