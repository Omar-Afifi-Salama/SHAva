// Delete notes without a page reload: intercepts .note-delete buttons
// and issues a DELETE request to /notes/:id (handled by NoteController.destroy).
document.addEventListener("click", async (event) => {
    const button = event.target.closest(".note-delete");
    if (!button) return;

    if (!confirm("Delete this note?")) return;

    try {
        const res = await fetch("/notes/" + button.dataset.id, {
            method: "DELETE"
        });

        if (!res.ok) throw new Error("Request failed: " + res.status);

        const card = button.closest(".note-card");
        card.classList.add("note-deleting");
        setTimeout(() => card.remove(), 300);
    } catch (err) {
        alert("Could not delete the note. Is the server still running?");
        console.error(err);
    }
});
