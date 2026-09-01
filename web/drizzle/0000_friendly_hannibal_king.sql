CREATE TABLE "ats_analyses" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"resume_id" uuid NOT NULL,
	"parse_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"overall_score" smallint NOT NULL,
	"band" varchar(20) NOT NULL,
	"parseability_score" smallint NOT NULL,
	"structure_score" smallint NOT NULL,
	"content_score" smallint NOT NULL,
	"skills_score" smallint NOT NULL,
	"contact_score" smallint NOT NULL,
	"rubric_version" varchar(20) NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "ats_findings" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"analysis_id" uuid NOT NULL,
	"code" varchar(60) NOT NULL,
	"category" varchar(30) NOT NULL,
	"severity" varchar(20) NOT NULL,
	"title" varchar(200) NOT NULL,
	"detail" text NOT NULL,
	"recommendation" text,
	"evidence" text,
	"line_start" integer,
	"line_end" integer,
	"points_lost" smallint DEFAULT 0 NOT NULL,
	"display_order" smallint DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE TABLE "interview_answers" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"question_id" uuid NOT NULL,
	"session_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"answer_text" text NOT NULL,
	"word_count" integer NOT NULL,
	"score" smallint NOT NULL,
	"structure_score" smallint NOT NULL,
	"specificity_score" smallint NOT NULL,
	"relevance_score" smallint NOT NULL,
	"clarity_score" smallint NOT NULL,
	"strengths" text,
	"improvements" text,
	"rubric_version" varchar(20) NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "interview_questions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"session_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"position" smallint NOT NULL,
	"kind" varchar(30) NOT NULL,
	"prompt" text NOT NULL,
	"focus_skill" varchar(100),
	"rationale" text,
	"expected_points" text
);
--> statement-breakpoint
CREATE TABLE "interview_sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"resume_id" uuid,
	"job_description_id" uuid,
	"focus" varchar(30) NOT NULL,
	"status" varchar(20) DEFAULT 'IN_PROGRESS' NOT NULL,
	"question_count" smallint NOT NULL,
	"answered_count" smallint DEFAULT 0 NOT NULL,
	"overall_score" smallint,
	"band" varchar(20),
	"blueprint_version" varchar(20) NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"completed_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "jd_match_skills" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"match_id" uuid NOT NULL,
	"normalized_name" varchar(100) NOT NULL,
	"display_name" varchar(100) NOT NULL,
	"category" varchar(30) NOT NULL,
	"status" varchar(20) NOT NULL,
	"required" boolean DEFAULT false NOT NULL,
	"priority" smallint DEFAULT 0 NOT NULL,
	"resume_evidence" text,
	"resume_line" integer,
	"jd_evidence" text,
	"jd_line" integer
);
--> statement-breakpoint
CREATE TABLE "jd_matches" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"job_description_id" uuid NOT NULL,
	"resume_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"overall_score" smallint NOT NULL,
	"band" varchar(20) NOT NULL,
	"required_skill_score" smallint NOT NULL,
	"optional_skill_score" smallint NOT NULL,
	"title_score" smallint NOT NULL,
	"experience_score" smallint NOT NULL,
	"matched_count" smallint DEFAULT 0 NOT NULL,
	"missing_count" smallint DEFAULT 0 NOT NULL,
	"rubric_version" varchar(20) NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "job_descriptions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"title" varchar(200) NOT NULL,
	"company" varchar(200),
	"location" varchar(150),
	"source_url" varchar(1000),
	"raw_text" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "parsed_contacts" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"parse_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"full_name" varchar(150),
	"email" varchar(320),
	"phone" varchar(40),
	"location" varchar(150),
	"linkedin_url" varchar(500),
	"github_url" varchar(500),
	"portfolio_url" varchar(500),
	"confidence" smallint NOT NULL,
	"line_start" integer,
	"line_end" integer
);
--> statement-breakpoint
CREATE TABLE "parsed_education" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"parse_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"institution" varchar(200),
	"degree" varchar(150),
	"field_of_study" varchar(150),
	"start_date" date,
	"end_date" date,
	"grade" varchar(20),
	"confidence" smallint NOT NULL,
	"line_start" integer,
	"line_end" integer
);
--> statement-breakpoint
CREATE TABLE "parsed_experience" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"parse_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"company" varchar(200),
	"job_title" varchar(200),
	"start_date" date,
	"end_date" date,
	"is_current" boolean DEFAULT false NOT NULL,
	"description" text,
	"confidence" smallint NOT NULL,
	"line_start" integer,
	"line_end" integer
);
--> statement-breakpoint
CREATE TABLE "parsed_skills" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"parse_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"name" varchar(100) NOT NULL,
	"normalized_name" varchar(100) NOT NULL,
	"category" varchar(30) NOT NULL,
	"confidence" smallint NOT NULL,
	"line_start" integer
);
--> statement-breakpoint
CREATE TABLE "resume_parses" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"resume_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"status" varchar(20) NOT NULL,
	"parser" varchar(30) NOT NULL,
	"raw_text" text,
	"page_count" smallint,
	"word_count" integer,
	"char_count" integer,
	"warnings" text DEFAULT '[]' NOT NULL,
	"error_message" text,
	"duration_ms" integer,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "resumes" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"original_filename" varchar(255) NOT NULL,
	"mime_type" varchar(100) NOT NULL,
	"size_bytes" integer NOT NULL,
	"checksum_sha256" varchar(64) NOT NULL,
	"content" "bytea" NOT NULL,
	"status" varchar(20) DEFAULT 'UPLOADED' NOT NULL,
	"is_primary" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"token_hash" varchar(64) NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"revoked_at" timestamp with time zone,
	"user_agent" varchar(300),
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"email" varchar(255) NOT NULL,
	"password_hash" varchar(100) NOT NULL,
	"full_name" varchar(120) NOT NULL,
	"role" varchar(20) DEFAULT 'USER' NOT NULL,
	"status" varchar(20) DEFAULT 'ACTIVE' NOT NULL,
	"failed_login_attempts" smallint DEFAULT 0 NOT NULL,
	"locked_until" timestamp with time zone,
	"last_login_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "ats_analyses" ADD CONSTRAINT "ats_analyses_resume_id_resumes_id_fk" FOREIGN KEY ("resume_id") REFERENCES "public"."resumes"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "ats_analyses" ADD CONSTRAINT "ats_analyses_parse_id_resume_parses_id_fk" FOREIGN KEY ("parse_id") REFERENCES "public"."resume_parses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "ats_analyses" ADD CONSTRAINT "ats_analyses_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "ats_findings" ADD CONSTRAINT "ats_findings_analysis_id_ats_analyses_id_fk" FOREIGN KEY ("analysis_id") REFERENCES "public"."ats_analyses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_answers" ADD CONSTRAINT "interview_answers_question_id_interview_questions_id_fk" FOREIGN KEY ("question_id") REFERENCES "public"."interview_questions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_answers" ADD CONSTRAINT "interview_answers_session_id_interview_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."interview_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_answers" ADD CONSTRAINT "interview_answers_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_questions" ADD CONSTRAINT "interview_questions_session_id_interview_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."interview_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_questions" ADD CONSTRAINT "interview_questions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_sessions" ADD CONSTRAINT "interview_sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_sessions" ADD CONSTRAINT "interview_sessions_resume_id_resumes_id_fk" FOREIGN KEY ("resume_id") REFERENCES "public"."resumes"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "interview_sessions" ADD CONSTRAINT "interview_sessions_job_description_id_job_descriptions_id_fk" FOREIGN KEY ("job_description_id") REFERENCES "public"."job_descriptions"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "jd_match_skills" ADD CONSTRAINT "jd_match_skills_match_id_jd_matches_id_fk" FOREIGN KEY ("match_id") REFERENCES "public"."jd_matches"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "jd_matches" ADD CONSTRAINT "jd_matches_job_description_id_job_descriptions_id_fk" FOREIGN KEY ("job_description_id") REFERENCES "public"."job_descriptions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "jd_matches" ADD CONSTRAINT "jd_matches_resume_id_resumes_id_fk" FOREIGN KEY ("resume_id") REFERENCES "public"."resumes"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "jd_matches" ADD CONSTRAINT "jd_matches_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "job_descriptions" ADD CONSTRAINT "job_descriptions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_contacts" ADD CONSTRAINT "parsed_contacts_parse_id_resume_parses_id_fk" FOREIGN KEY ("parse_id") REFERENCES "public"."resume_parses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_contacts" ADD CONSTRAINT "parsed_contacts_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_education" ADD CONSTRAINT "parsed_education_parse_id_resume_parses_id_fk" FOREIGN KEY ("parse_id") REFERENCES "public"."resume_parses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_education" ADD CONSTRAINT "parsed_education_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_experience" ADD CONSTRAINT "parsed_experience_parse_id_resume_parses_id_fk" FOREIGN KEY ("parse_id") REFERENCES "public"."resume_parses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_experience" ADD CONSTRAINT "parsed_experience_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_skills" ADD CONSTRAINT "parsed_skills_parse_id_resume_parses_id_fk" FOREIGN KEY ("parse_id") REFERENCES "public"."resume_parses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "parsed_skills" ADD CONSTRAINT "parsed_skills_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "resume_parses" ADD CONSTRAINT "resume_parses_resume_id_resumes_id_fk" FOREIGN KEY ("resume_id") REFERENCES "public"."resumes"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "resume_parses" ADD CONSTRAINT "resume_parses_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "resumes" ADD CONSTRAINT "resumes_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "sessions" ADD CONSTRAINT "sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "ix_ats_resume_created" ON "ats_analyses" USING btree ("resume_id","created_at");--> statement-breakpoint
CREATE INDEX "ix_findings_analysis" ON "ats_findings" USING btree ("analysis_id","display_order");--> statement-breakpoint
CREATE UNIQUE INDEX "ux_answers_question" ON "interview_answers" USING btree ("question_id");--> statement-breakpoint
CREATE INDEX "ix_answers_session" ON "interview_answers" USING btree ("session_id");--> statement-breakpoint
CREATE UNIQUE INDEX "ux_questions_session_position" ON "interview_questions" USING btree ("session_id","position");--> statement-breakpoint
CREATE INDEX "ix_sessions_user_created" ON "interview_sessions" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "ix_match_skills_match" ON "jd_match_skills" USING btree ("match_id","priority");--> statement-breakpoint
CREATE INDEX "ix_matches_jd_created" ON "jd_matches" USING btree ("job_description_id","created_at");--> statement-breakpoint
CREATE INDEX "ix_jd_user_created" ON "job_descriptions" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE UNIQUE INDEX "ux_parsed_skills_parse_name" ON "parsed_skills" USING btree ("parse_id","normalized_name");--> statement-breakpoint
CREATE INDEX "ix_parsed_skills_normalized" ON "parsed_skills" USING btree ("normalized_name");--> statement-breakpoint
CREATE INDEX "ix_parses_resume_created" ON "resume_parses" USING btree ("resume_id","created_at");--> statement-breakpoint
CREATE INDEX "ix_resumes_user_created" ON "resumes" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE UNIQUE INDEX "ux_resumes_user_checksum" ON "resumes" USING btree ("user_id","checksum_sha256");--> statement-breakpoint
CREATE UNIQUE INDEX "ux_sessions_token_hash" ON "sessions" USING btree ("token_hash");--> statement-breakpoint
CREATE INDEX "ix_sessions_user" ON "sessions" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "ix_sessions_expires" ON "sessions" USING btree ("expires_at");--> statement-breakpoint
CREATE UNIQUE INDEX "ux_users_email_lower" ON "users" USING btree (lower("email"));--> statement-breakpoint
CREATE INDEX "ix_users_created" ON "users" USING btree ("created_at");