import { Construction } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";

export function PlaceholderPage({ title, phase, description }: { title: string; phase: string; description: string }) {
  return (
    <>
      <PageHeader title={title} description={`Módulo previsto para a ${phase}.`} />
      <EmptyState icon={Construction} title={`${title} ainda não está disponível`} description={description} />
    </>
  );
}
